package kr.haedal.ondal.notice.service;

import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.notice.dto.NoticeResponse;
import kr.haedal.ondal.notice.entity.Notice;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Notice 목록 → NoticeResponse 목록. 요청자에 따라 달라지는 필드(canEdit·canDelete)와 작성자 직책을 채운다.
 * QuestionResponseAssembler 패턴 - 공지가 여러 분반에 걸치므로 관련 분반 명부를 한 번에 조회한다 (cohortId → (userId → role)).
 * 호출하는 쪽의 트랜잭션 안에서 실행된다는 전제 - 자체 @Transactional 은 없다. author·cohort 는 리포지토리가 fetch join 해 온다.
 *
 * - 전체 공지: 작성자 직책은 분반 역할 없이 정한다(관리자 → 해구르르). canEdit·canDelete 는 관리자만
 * - 분반 공지: 작성자 직책은 그 분반 역할. canEdit·canDelete 는 CohortAuthorizer.canManage(ACTIVE && 운영진 이상)
 */
@Component
public class NoticeResponseAssembler {

    private final EnrollmentRepository enrollmentRepository;
    private final CohortAuthorizer cohortAuthorizer;

    public NoticeResponseAssembler(EnrollmentRepository enrollmentRepository, CohortAuthorizer cohortAuthorizer) {
        this.enrollmentRepository = enrollmentRepository;
        this.cohortAuthorizer = cohortAuthorizer;
    }

    public NoticeResponse toResponse(Notice notice, User viewer) {
        return toResponses(List.of(notice), viewer).get(0);
    }

    public List<NoticeResponse> toResponses(List<Notice> notices, User viewer) {
        if (notices.isEmpty()) {
            return List.of();
        }
        Set<Long> cohortIds = notices.stream()
                .filter(n -> !n.isGlobal())
                .map(n -> n.getCohort().getId())
                .collect(Collectors.toSet());
        // 관련 분반 명부 1번 조회 - 작성자 직책과 요청자의 분반 역할을 함께 얻는다
        Map<Long, Map<Long, EnrollmentRole>> rolesByCohort = cohortIds.isEmpty()
                ? Map.of()
                : enrollmentRepository.findAllByCohortIdInWithUser(cohortIds).stream()
                        .collect(Collectors.groupingBy(e -> e.getCohort().getId(),
                                Collectors.toMap(e -> e.getUser().getId(), Enrollment::getRole)));

        return notices.stream()
                .map(notice -> {
                    if (notice.isGlobal()) {
                        return NoticeResponse.of(notice, UserSummary.of(notice.getAuthor(), null), viewer.isAdmin());
                    }
                    Cohort cohort = notice.getCohort();
                    Map<Long, EnrollmentRole> roles = rolesByCohort.getOrDefault(cohort.getId(), Map.of());
                    boolean canManage = cohortAuthorizer.canManage(viewer, cohort, roles.get(viewer.getId()));
                    // 분반에서 빠진 작성자(roles 에 없음)는 일반 수강생으로 표시된다 - 글은 남고 직책만 사라진다
                    return NoticeResponse.of(notice, UserSummary.of(notice.getAuthor(), roles.get(notice.getAuthor().getId())), canManage);
                })
                .toList();
    }
}
