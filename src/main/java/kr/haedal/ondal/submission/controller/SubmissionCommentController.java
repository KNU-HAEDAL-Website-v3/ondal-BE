package kr.haedal.ondal.submission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.submission.dto.SubmissionCommentRequest;
import kr.haedal.ondal.submission.dto.SubmissionResponse;
import kr.haedal.ondal.submission.service.SubmissionService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제출 코멘트 API (#45~#46) - 운영진 이상이 제출 1건에 코멘트 1개를 남기고(덮어쓰기) 지운다. 점수는 없다 (docs submission/design.md 결정 18).
 * 학생은 기존 제출 상세(#20)·내 이력(#19)의 comment / hasComment 로 읽는다 - 읽기 API 추가 없음.
 */
@Tag(name = "Submission")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/assignments/{assignmentId}/submissions/{submissionId}/comment")
public class SubmissionCommentController {

    private final SubmissionService submissionService;

    public SubmissionCommentController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "[운영진] 제출 코멘트 남기기·덮어쓰기 - 제출 1건에 1개. 응답은 갱신된 제출 상세. 보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PutMapping
    public SubmissionResponse comment(@PathVariable Long cohortId,
                                      @PathVariable Long assignmentId,
                                      @PathVariable Long submissionId,
                                      @RequestBody @Valid SubmissionCommentRequest request,
                                      @LoginUser User me) {
        return submissionService.comment(cohortId, assignmentId, submissionId, request, me);
    }

    @Operation(summary = "[운영진] 제출 코멘트 지우기 - 없어도 204(멱등). 보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long cohortId,
                      @PathVariable Long assignmentId,
                      @PathVariable Long submissionId,
                      @LoginUser User me) {
        submissionService.clearComment(cohortId, assignmentId, submissionId, me);
    }
}
