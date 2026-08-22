package kr.haedal.hoj.cohort.service;

import kr.haedal.hoj.cohort.entity.Cohort;

import kr.haedal.hoj.auth.authorization.CohortAuthorizer;
import kr.haedal.hoj.cohort.dto.CohortResponse;
import kr.haedal.hoj.enrollment.entity.Enrollment;
import kr.haedal.hoj.enrollment.repository.EnrollmentRepository;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.user.entity.RoleTitle;
import kr.haedal.hoj.user.entity.User;
import kr.haedal.hoj.user.dto.UserSummary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cohort 목록 → CohortResponse 목록. 요청자에 따라 달라지는 필드(myRole·canManage·studentCount)를 채운다.
 *
 * CohortService(분반 API)와 EnrollmentService(/api/me/cohorts)가 둘 다 쓰므로 별도 컴포넌트로 뺐다
 * (서비스끼리 서로 주입하면 순환이 생긴다). 분반 N개에 대해 Enrollment 조회는 1번만 한다.
 * 호출하는 쪽의 트랜잭션 안에서 실행된다는 전제 - 자체 @Transactional은 없다.
 */
@Component
public class CohortResponseAssembler {

    private final EnrollmentRepository enrollmentRepository;
    private final CohortAuthorizer cohortAuthorizer;

    public CohortResponseAssembler(EnrollmentRepository enrollmentRepository, CohortAuthorizer cohortAuthorizer) {
        this.enrollmentRepository = enrollmentRepository;
        this.cohortAuthorizer = cohortAuthorizer;
    }

    public CohortResponse toResponse(Cohort cohort, User viewer) {
        return toResponses(List.of(cohort), viewer).get(0);
    }

    public List<CohortResponse> toResponses(List<Cohort> cohorts, User viewer) {
        if (cohorts.isEmpty()) {
            return List.of();
        }
        List<Long> cohortIds = cohorts.stream().map(Cohort::getId).toList();
        Map<Long, List<Enrollment>> enrollmentsByCohort = enrollmentRepository.findAllByCohortIdInWithUser(cohortIds).stream()
                .collect(Collectors.groupingBy(e -> e.getCohort().getId())); // 프록시의 getId()는 DB를 치지 않는다

        return cohorts.stream()
                .map(cohort -> toResponse(cohort, enrollmentsByCohort.getOrDefault(cohort.getId(), List.of()), viewer))
                .toList();
    }

    private CohortResponse toResponse(Cohort cohort, List<Enrollment> enrollments, User viewer) {
        // 운영진은 이름만 - 학생에게 내려가는 응답이므로 loginId·globalRole 을 싣지 않는다 (UserSummary)
        List<UserSummary> operators = enrollments.stream()
                .filter(Enrollment::isOperator)
                .map(e -> UserSummary.of(e.getUser(), e.getRole()))
                .sorted(Comparator.comparing(UserSummary::name))
                .toList();

        EnrollmentRole myRole = enrollments.stream()
                .filter(e -> e.getUser().getId().equals(viewer.getId()))
                .map(Enrollment::getRole)
                .findFirst()
                .orElse(null);

        RoleTitle myTitle = RoleTitle.of(viewer, myRole);
        boolean canManage = cohortAuthorizer.canManage(viewer, cohort, myRole);

        // 수강생 수는 운영진·관리자에게만 - 학생에게는 타인 정보(인원 포함)를 내려주지 않는다
        boolean canSeeCount = viewer.isAdmin() || myRole == EnrollmentRole.OPERATOR;
        Integer studentCount = canSeeCount
                ? (int) enrollments.stream().filter(e -> !e.isOperator()).count()
                : null;

        return CohortResponse.of(cohort, operators, studentCount, myRole, myTitle, canManage);
    }
}
