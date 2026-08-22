package kr.haedal.hoj.auth.authorization;

import kr.haedal.hoj.cohort.entity.Cohort;
import kr.haedal.hoj.enrollment.entity.Enrollment;
import kr.haedal.hoj.enrollment.repository.EnrollmentRepository;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 분반 권한 규칙의 단일 출처 — 두 규칙(요청 통과 조건 isAllowed / 운영 버튼 canManage)이 이 클래스에만 있다.
 * - AuthorizationInterceptor 는 isAllowed 를, CohortResponseAssembler 는 canManage 를 쓴다.
 *   어느 한쪽 규칙을 바꿀 때 여기만 고치면 되고, 규칙이 두 군데에 복제되어 서로 어긋나는 일이 없다.
 * - permissions.md §2 판정 순서 중 ②(ADMIN 통과) ③(Enrollment 있나) ④(role 충족)를 담당한다. ①(로그인)은 AuthInterceptor.
 */
@Component
public class CohortAuthorizer {

    private final EnrollmentRepository enrollmentRepository;

    public CohortAuthorizer(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    /** 이 분반에서의 소속 역할. 비소속(ADMIN 포함)이면 empty. */
    public Optional<EnrollmentRole> roleOf(User user, Long cohortId) {
        return enrollmentRepository.findByCohortIdAndUserId(cohortId, user.getId())
                .map(Enrollment::getRole);
    }

    /** ② ADMIN이면 통과 → ③ 소속이 있고 → ④ 요구 역할을 만족하는가 */
    public boolean isAllowed(User user, Long cohortId, EnrollmentRole required) {
        if (user.isAdmin()) {
            return true;
        }
        return roleOf(user, cohortId)
                .map(role -> role.satisfies(required))
                .orElse(false);
    }

    /**
     * 프론트의 "운영 기능 진입 버튼" 판정값. 보관된 분반은 누구도 운영할 수 없으므로 false.
     * (ADMIN이 보관 분반을 손보려면 restore 먼저 — 그 버튼은 관리자 화면 소관, globalRole로 판단)
     */
    public boolean canManage(User user, Cohort cohort, EnrollmentRole myRoleOrNull) {
        if (!cohort.isActive()) {
            return false;
        }
        return user.isAdmin() || myRoleOrNull == EnrollmentRole.OPERATOR;
    }
}
