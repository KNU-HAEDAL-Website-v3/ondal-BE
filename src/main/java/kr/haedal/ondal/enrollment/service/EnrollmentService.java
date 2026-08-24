package kr.haedal.ondal.enrollment.service;

import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;

import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.cohort.service.CohortResponseAssembler;
import kr.haedal.ondal.cohort.dto.CohortResponse;
import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.dto.MemberResponse;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;
import kr.haedal.ondal.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 소속(Enrollment) 관리 - 내 분반 목록, 분반 명부, 수강생 배정/제외, 운영진 지정/해제.
 *
 * 규약: 분반 스코프의 "쓰기"는 첫 줄에서 cohort.ensureActive() - 보관된 분반은 409.
 */
@Service
@Transactional
public class EnrollmentService {

    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final CohortResponseAssembler assembler;

    public EnrollmentService(CohortRepository cohortRepository,
                             EnrollmentRepository enrollmentRepository,
                             UserRepository userRepository,
                             UserService userService,
                             CohortResponseAssembler assembler) {
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.assembler = assembler;
    }

    /** 내가 소속된 모든 분반 - 보관된 분반도 포함(status로 구분). ACTIVE가 먼저, 그 안에서는 최신순. */
    @Transactional(readOnly = true)
    public List<CohortResponse> findMyCohorts(User me) {
        List<Cohort> cohorts = enrollmentRepository.findAllByUserIdWithCohort(me.getId()).stream()
                .map(Enrollment::getCohort)
                .sorted(Comparator.comparing(Cohort::isArchived)                       // false(ACTIVE) 가 먼저
                        .thenComparing(Cohort::getCreatedAt, Comparator.reverseOrder()))  // 그 안에서는 최신순
                .toList();
        return assembler.toResponses(cohorts, me);
    }

    /** 분반 명부 전체 (운영진 먼저). 호출자 권한은 @CohortRole(OPERATOR)가 이미 확인했다. */
    @Transactional(readOnly = true)
    public List<MemberResponse> findMembers(Long cohortId) {
        requireCohort(cohortId);
        return enrollmentRepository.findAllByCohortIdWithUser(cohortId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    /**
     * 수강생 일괄 배정 (UC-O2, 운영진 이상 API). 명단 붙여넣기 → 갱신된 명부 반환.
     * FE가 배정 직후 명부를 다시 조회할 필요 없게, 응답을 GET /members 와 같은 모양으로 준다.
     */
    public List<MemberResponse> assignStudents(Long cohortId, List<String> loginIds) {
        assign(cohortId, loginIds, EnrollmentRole.STUDENT);
        return findMembers(cohortId);
    }

    /**
     * loginId 목록을 role로 일괄 소속시킨다 (분반 생성 시 운영진 지정, 수강생 명단 붙여넣기).
     * - 없는 사람은 만들고(find-or-create), 같은 role이면 그대로(멱등), 다른 role로 이미 소속이면 409 - 역할을 바꾸지 않는다.
     */
    public void assign(Long cohortId, List<String> loginIds, EnrollmentRole role) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        for (String loginId : new LinkedHashSet<>(loginIds)) { // 중복 loginId 제거, 입력 순서 유지
            User user = userService.findOrCreateMember(loginId);
            enrollmentRepository.findByCohortIdAndUserId(cohortId, user.getId())
                    .ifPresentOrElse(
                            existing -> {
                                if (existing.getRole() != role) {
                                    throw new ConflictException("이미 " + existing.getRole() + " 로 소속된 사용자입니다: " + loginId);
                                }
                            },
                            () -> enrollmentRepository.save(Enrollment.create(cohort, user, role))
                    );
        }
    }

    /**
     * 운영진 지정 (ADMIN 전용 API). 미소속이면 OPERATOR로 소속시키고, STUDENT면 승격, 이미 OPERATOR면 그대로. 멱등.
     * 승격은 이 경로 하나뿐이다 - 강등 API는 없다 (해제 후 다시 수강생으로 배정).
     */
    public MemberResponse promoteToOperator(Long cohortId, String loginId) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        User user = userService.findOrCreateMember(loginId);
        Enrollment enrollment = enrollmentRepository.findByCohortIdAndUserId(cohortId, user.getId())
                .orElseGet(() -> enrollmentRepository.save(Enrollment.create(cohort, user, EnrollmentRole.OPERATOR)));
        if (!enrollment.isOperator()) {
            enrollment.promoteToOperator();
        }
        return MemberResponse.from(enrollment);
    }

    /**
     * 소속 해제. expected 역할의 Enrollment만 지운다 - /operators/{loginId} 는 OPERATOR만, /students/{loginId} 는 STUDENT만.
     * 대상이 없거나(미소속·모르는 loginId) 역할이 다르면 404 (규약: 삭제 = 204, 대상 없으면 404).
     */
    public void remove(Long cohortId, String loginId, EnrollmentRole expected) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        String roleName = expected == EnrollmentRole.OPERATOR ? "운영진" : "수강생";
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new NotFoundException("해당 분반의 " + roleName + "이 아닙니다: " + loginId));
        Enrollment enrollment = enrollmentRepository.findByCohortIdAndUserId(cohortId, user.getId())
                .filter(e -> e.getRole() == expected)
                .orElseThrow(() -> new NotFoundException("해당 분반의 " + roleName + "이 아닙니다: " + loginId));
        enrollmentRepository.delete(enrollment);
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }
}
