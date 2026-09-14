package kr.haedal.ondal.qna.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.qna.dto.AnswerCreateRequest;
import kr.haedal.ondal.qna.dto.AnswerResponse;
import kr.haedal.ondal.qna.dto.AnswerUpdateRequest;
import kr.haedal.ondal.qna.service.AnswerService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Q&A 답변 API (#41~#44) - 조회·등록은 분반 소속 누구나, 수정은 작성자, 삭제는 작성자 또는 운영진 이상 (QuestionController 와 동일 규칙).
 * 메서드마다: 권한 어노테이션 → 검증(@Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 */
@Tag(name = "Answer", description = "Q&A 답변 - 조회·등록은 분반 소속자, 수정은 작성자, 삭제는 작성자 또는 운영진 이상. 채택·좋아요 없음")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/questions/{questionId}/answers")
public class AnswerController {

    private final AnswerService answerService;

    public AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    @Operation(summary = "답변 목록 - 오래된 순(대화 흐름). canEdit·canDelete 는 요청자 의존")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping
    public List<AnswerResponse> list(@PathVariable Long cohortId, @PathVariable Long questionId, @LoginUser User user) {
        return answerService.findAll(cohortId, questionId, user);
    }

    @Operation(summary = "답변 등록 - 분반 소속 누구나. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @PostMapping
    public ResponseEntity<AnswerResponse> create(@PathVariable Long cohortId,
                                                 @PathVariable Long questionId,
                                                 @RequestBody @Valid AnswerCreateRequest request,
                                                 @LoginUser User user) {
        AnswerResponse created = answerService.create(cohortId, questionId, request, user);
        return ResponseEntity.created(URI.create("/api/cohorts/" + cohortId + "/questions/" + questionId + "/answers/" + created.id()))
                .body(created);
    }

    @Operation(summary = "답변 수정 - 작성자 본인만, 아니면 403. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @PutMapping("/{answerId}")
    public AnswerResponse update(@PathVariable Long cohortId,
                                 @PathVariable Long questionId,
                                 @PathVariable Long answerId,
                                 @RequestBody @Valid AnswerUpdateRequest request,
                                 @LoginUser User user) {
        return answerService.update(cohortId, questionId, answerId, request, user);
    }

    @Operation(summary = "답변 삭제 - 작성자 본인 또는 운영진 이상, 아니면 403. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @DeleteMapping("/{answerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long cohortId, @PathVariable Long questionId, @PathVariable Long answerId, @LoginUser User user) {
        answerService.delete(cohortId, questionId, answerId, user);
    }
}
