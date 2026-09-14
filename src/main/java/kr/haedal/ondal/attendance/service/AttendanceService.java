package kr.haedal.ondal.attendance.service;

import kr.haedal.ondal.attendance.dto.AttendanceMarkRequest;
import kr.haedal.ondal.attendance.dto.AttendanceRosterResponse;
import kr.haedal.ondal.attendance.dto.AttendanceRosterResponse.AttendanceRow;
import kr.haedal.ondal.attendance.dto.AttendanceStats;
import kr.haedal.ondal.attendance.dto.MyAttendanceResponse;
import kr.haedal.ondal.attendance.dto.MyAttendanceResponse.MyAttendanceRecord;
import kr.haedal.ondal.attendance.dto.SessionResponse;
import kr.haedal.ondal.attendance.entity.Attendance;
import kr.haedal.ondal.attendance.entity.AttendanceStatus;
import kr.haedal.ondal.attendance.entity.Session;
import kr.haedal.ondal.attendance.repository.AttendanceRepository;
import kr.haedal.ondal.attendance.repository.SessionRepository;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.user.dto.UserResponse;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 출석 - 차시 명부(운영진)·표시(일괄 upsert)·내 출석(학생). 권한은 컨트롤러 어노테이션이 확인했다.
 * - 명부의 행 = 현재 STUDENT 명단(이름순), 운영진은 없다 (현황판 선례 - docs attendance/design.md 결정 5)
 * - 표시 대상은 이 분반의 STUDENT 여야 한다 - 아니면 404 "해당 분반의 수강생이 아닙니다"
 * - status null 은 기록 삭제(미확인). 응답은 갱신된 명부 전체 (결정 4)
 * - 출석률 등 집계는 AttendanceStats.of 한 곳에서 (결정 6)
 */
@Service
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             SessionRepository sessionRepository,
                             SessionService sessionService,
                             CohortRepository cohortRepository,
                             EnrollmentRepository enrollmentRepository) {
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /** #38 차시 명부 */
    @Transactional(readOnly = true)
    public AttendanceRosterResponse roster(Long cohortId, Long sessionId) {
        requireCohort(cohortId);
        Session session = sessionService.requireSession(cohortId, sessionId);
        return buildRoster(cohortId, session);
    }

    /** #39 표시 - 일괄 upsert 후 갱신된 명부 */
    public AttendanceRosterResponse mark(Long cohortId, Long sessionId, AttendanceMarkRequest request, User marker) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Session session = sessionService.requireSession(cohortId, sessionId);

        Map<String, User> studentsByLoginId = students(cohortId).stream()
                .collect(Collectors.toMap(e -> e.getUser().getLoginId(), Enrollment::getUser));
        Map<Long, Attendance> existingByUserId = attendanceRepository.findAllBySessionIdWithUser(sessionId).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));

        // 같은 loginId 가 여러 번이면 마지막 값 - 입력 순서 유지
        Map<String, AttendanceStatus> lastByLoginId = new LinkedHashMap<>();
        for (AttendanceMarkRequest.Record record : request.records()) {
            lastByLoginId.put(record.loginId().strip(), record.status());
        }
        for (Map.Entry<String, AttendanceStatus> entry : lastByLoginId.entrySet()) {
            User student = studentsByLoginId.get(entry.getKey());
            if (student == null) {
                throw new NotFoundException("해당 분반의 수강생이 아닙니다: " + entry.getKey());
            }
            Attendance existing = existingByUserId.get(student.getId());
            AttendanceStatus status = entry.getValue();
            if (status == null) {
                if (existing != null) {
                    attendanceRepository.delete(existing);
                }
            } else if (existing != null) {
                existing.remark(status, marker);
            } else {
                attendanceRepository.save(Attendance.mark(session, student, status, marker));
            }
        }
        attendanceRepository.flush(); // 뒤이은 명부 조회가 지금 반영한 기록을 읽게
        return buildRoster(cohortId, session);
    }

    /** #40 내 출석 - 분반 누계 + 차시별 기록(최신 차시 먼저) */
    @Transactional(readOnly = true)
    public MyAttendanceResponse my(Long cohortId, User viewer) {
        requireCohort(cohortId);
        List<Session> sessions = sessionRepository.findAllByCohortIdOrderByHeldOnAscSessionNoAsc(cohortId);
        Map<Long, Long> countBySession = SessionService.countBySession(attendanceRepository.findAllByCohortIdWithSession(cohortId));
        Map<Long, Attendance> mineBySession = attendanceRepository.findAllByCohortIdAndUserIdWithSession(cohortId, viewer.getId()).stream()
                .collect(Collectors.toMap(a -> a.getSession().getId(), Function.identity()));

        List<MyAttendanceRecord> records = sessions.stream()
                .sorted(Comparator.comparing(Session::getHeldOn).thenComparing(Session::getSessionNo).reversed())
                .map(s -> {
                    Attendance mine = mineBySession.get(s.getId());
                    return new MyAttendanceRecord(SessionResponse.of(s, countBySession.getOrDefault(s.getId(), 0L)),
                            mine == null ? null : mine.getStatus(), mine == null ? null : mine.getCheckedAt());
                })
                .toList();
        return new MyAttendanceResponse(statsOf(mineBySession.values(), sessions.size()), records);
    }

    // ---- 조립 ---------------------------------------------------------------------------

    private AttendanceRosterResponse buildRoster(Long cohortId, Session session) {
        List<Enrollment> students = students(cohortId);
        Map<Long, Attendance> thisSessionByUser = attendanceRepository.findAllBySessionIdWithUser(session.getId()).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), Function.identity()));
        // 분반 전체 기록 1회 조회 → 학생별 누계 (출석률 열)
        Map<Long, List<Attendance>> cohortRecordsByUser = attendanceRepository.findAllByCohortIdWithSession(cohortId).stream()
                .collect(Collectors.groupingBy(a -> a.getUser().getId()));
        int totalSessions = (int) sessionRepository.countByCohortId(cohortId);

        List<AttendanceRow> rows = students.stream()
                .map(e -> {
                    Attendance mine = thisSessionByUser.get(e.getUser().getId());
                    AttendanceStats stats = statsOf(cohortRecordsByUser.getOrDefault(e.getUser().getId(), List.of()), totalSessions);
                    return new AttendanceRow(UserResponse.from(e.getUser()),
                            mine == null ? null : mine.getStatus(), mine == null ? null : mine.getCheckedAt(), stats);
                })
                .toList();

        // 이 차시 요약 - 명단에 있는 학생의 기록만 센다 (배정 해제된 학생의 기록은 행에도 요약에도 없다)
        List<Attendance> onRoster = students.stream()
                .map(e -> thisSessionByUser.get(e.getUser().getId()))
                .filter(a -> a != null)
                .toList();
        return new AttendanceRosterResponse(SessionResponse.of(session, thisSessionByUser.size()),
                statsOf(onRoster, rows.size()), rows);
    }

    /** 현재 STUDENT 명단 - 이름순 (운영진 제외) */
    private List<Enrollment> students(Long cohortId) {
        return enrollmentRepository.findAllByCohortIdWithUser(cohortId).stream()
                .filter(e -> e.getRole() == EnrollmentRole.STUDENT)
                .sorted(Comparator.comparing((Enrollment e) -> e.getUser().getName()).thenComparing(e -> e.getUser().getId()))
                .toList();
    }

    /** total = 기준 개수(명부 요약이면 명단 수, 학생 누계면 차시 수) - 기록 없는 만큼이 unchecked */
    private static AttendanceStats statsOf(Iterable<Attendance> records, int total) {
        int present = 0;
        int late = 0;
        int absent = 0;
        for (Attendance a : records) {
            switch (a.getStatus()) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
            }
        }
        return AttendanceStats.of(present, late, absent, Math.max(0, total - present - late - absent));
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }
}
