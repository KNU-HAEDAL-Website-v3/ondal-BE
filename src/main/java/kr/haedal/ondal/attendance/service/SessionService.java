package kr.haedal.ondal.attendance.service;

import kr.haedal.ondal.attendance.dto.SessionCreateRequest;
import kr.haedal.ondal.attendance.dto.SessionResponse;
import kr.haedal.ondal.attendance.dto.SessionUpdateRequest;
import kr.haedal.ondal.attendance.entity.Attendance;
import kr.haedal.ondal.attendance.entity.Session;
import kr.haedal.ondal.attendance.repository.AttendanceRepository;
import kr.haedal.ondal.attendance.repository.SessionRepository;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 차시 CRUD - Assignment 슬라이스 패턴. 권한(분반 소속·운영진)은 컨트롤러 어노테이션이 이미 확인했다.
 * - 하위 id 조회는 findByIdAndCohortId - 경로의 cohortId 와 불일치·부재면 404
 * - 쓰기는 첫 줄에서 cohort.ensureActive() - 보관 분반이면 409
 * - 번호는 분반 안 유일 - 등록 시 비우면 최대 + 1, 지정·수정 시 중복이면 409 CONFLICT (문제 번호 채번과 같은 방식)
 * - 삭제는 출석 기록을 서비스에서 연쇄 삭제 (docs attendance/design.md 결정 9)
 */
@Service
@Transactional
public class SessionService {

    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final CohortRepository cohortRepository;

    public SessionService(SessionRepository sessionRepository,
                          AttendanceRepository attendanceRepository,
                          CohortRepository cohortRepository) {
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.cohortRepository = cohortRepository;
    }

    /** 목록 - 날짜 → 번호 오름차순. 기록 수는 분반 기록 1회 조회로 채운다 */
    @Transactional(readOnly = true)
    public List<SessionResponse> findAll(Long cohortId) {
        requireCohort(cohortId);
        Map<Long, Long> countBySession = attendanceRepository.findAllByCohortIdWithSession(cohortId).stream()
                .collect(Collectors.groupingBy(a -> a.getSession().getId(), Collectors.counting()));
        return sessionRepository.findAllByCohortIdOrderByHeldOnAscSessionNoAsc(cohortId).stream()
                .map(s -> SessionResponse.of(s, countBySession.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    public SessionResponse create(Long cohortId, SessionCreateRequest request) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Integer sessionNo = request.sessionNo() != null ? request.sessionNo() : nextSessionNo(cohortId);
        requireUniqueSessionNo(cohortId, sessionNo, null);
        Session saved = sessionRepository.save(Session.create(cohort, sessionNo, blankToNull(request.title()), request.heldOn()));
        return SessionResponse.of(saved, 0);
    }

    public SessionResponse update(Long cohortId, Long sessionId, SessionUpdateRequest request) {
        requireCohort(cohortId).ensureActive();
        Session session = requireSession(cohortId, sessionId);
        requireUniqueSessionNo(cohortId, request.sessionNo(), session);
        session.update(request.sessionNo(), blankToNull(request.title()), request.heldOn());
        return SessionResponse.of(session, attendanceRepository.countBySessionId(sessionId));
    }

    /** 삭제 = 출석 기록 연쇄 삭제 후 차시 삭제 (DB FK 는 RESTRICT - 순서 누락은 에러로 드러난다) */
    public void delete(Long cohortId, Long sessionId) {
        requireCohort(cohortId).ensureActive();
        Session session = requireSession(cohortId, sessionId);
        attendanceRepository.deleteAllBySessionId(sessionId);
        sessionRepository.delete(session);
    }

    /** 다른 서비스(출석)가 쓰는 스코프 조회 - 불일치·부재 404 */
    public Session requireSession(Long cohortId, Long sessionId) {
        return sessionRepository.findByIdAndCohortId(sessionId, cohortId)
                .orElseThrow(() -> new NotFoundException("차시를 찾을 수 없습니다."));
    }

    private Integer nextSessionNo(Long cohortId) {
        Integer max = sessionRepository.findMaxSessionNo(cohortId);
        return max == null ? 1 : max + 1;
    }

    /** 같은 분반의 다른 차시가 이 번호를 쓰면 409. self 는 수정 중인 차시(자기 번호 유지는 허용) */
    private void requireUniqueSessionNo(Long cohortId, Integer sessionNo, Session self) {
        if (self != null && sessionNo.equals(self.getSessionNo())) {
            return;
        }
        if (sessionRepository.existsByCohortIdAndSessionNo(cohortId, sessionNo)) {
            throw new ConflictException("이미 사용 중인 차시 번호입니다: " + sessionNo);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }

    /** 명부·내 출석 조립에서 쓰는 (차시 id → 기록 수) - 정적 헬퍼로 한 곳에 */
    static Map<Long, Long> countBySession(List<Attendance> records) {
        return records.stream().collect(Collectors.groupingBy(a -> a.getSession().getId(), Collectors.counting()));
    }

    static <T> Map<Long, T> byId(List<T> items, Function<T, Long> idOf) {
        return items.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }
}
