package kr.haedal.ondal.qna.service;

import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.qna.dto.AnswerResponse;
import kr.haedal.ondal.qna.entity.Answer;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answer 목록 → AnswerResponse 목록. QuestionResponseAssembler 와 같은 규칙 - 분반 명부 1회 조회로 작성자 직책·요청자 역할을 얻는다.
 * canEdit: 작성자 본인 && ACTIVE / canDelete: canEdit || 운영진 이상(canManage)
 */
@Component
public class AnswerResponseAssembler {

    private final EnrollmentRepository enrollmentRepository;
    private final CohortAuthorizer cohortAuthorizer;

    public AnswerResponseAssembler(EnrollmentRepository enrollmentRepository, CohortAuthorizer cohortAuthorizer) {
        this.enrollmentRepository = enrollmentRepository;
        this.cohortAuthorizer = cohortAuthorizer;
    }

    public AnswerResponse toResponse(Answer answer, Cohort cohort, User viewer) {
        return toResponses(List.of(answer), cohort, viewer).get(0);
    }

    public List<AnswerResponse> toResponses(List<Answer> answers, Cohort cohort, User viewer) {
        if (answers.isEmpty()) {
            return List.of();
        }
        Map<Long, EnrollmentRole> roles = enrollmentRepository.findAllByCohortIdWithUser(cohort.getId()).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), Enrollment::getRole));
        boolean canModerate = cohortAuthorizer.canManage(viewer, cohort, roles.get(viewer.getId()));

        return answers.stream()
                .map(answer -> {
                    boolean canEdit = cohort.isActive() && answer.isWrittenBy(viewer);
                    return AnswerResponse.of(answer, UserSummary.of(answer.getAuthor(), roles.get(answer.getAuthor().getId())),
                            canEdit, canEdit || canModerate);
                })
                .toList();
    }
}
