package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.problem.dto.PracticeSubmitRequest;
import kr.haedal.ondal.problem.service.PracticeSubmissionService;
import kr.haedal.ondal.submission.dto.SubmissionResponse;
import kr.haedal.ondal.submission.dto.SubmissionSummary;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HOJ 연습 제출 API (#61~#63) - 분반과 무관하게 문제를 풀어 채점받는다.
 * 로그인한 누구나 제출할 수 있고, 조회는 언제나 본인 것만 (남의 연습 제출은 404).
 */
@Tag(name = "Problem", description = "HOJ 연습 제출 - 마감·지각·코멘트 없음, 코드만")
@RestController
@RequestMapping("/api/problems/{problemId}/submissions")
public class PracticeSubmissionController {

    private final PracticeSubmissionService practiceSubmissionService;

    public PracticeSubmissionController(PracticeSubmissionService practiceSubmissionService) {
        this.practiceSubmissionService = practiceSubmissionService;
    }

    @Operation(summary = "연습 제출 - 저장 즉시 채점 큐에 오른다(응답의 judge.status 는 PENDING). 테스트케이스 없는 문제면 409")
    @LoginOnly
    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(@PathVariable Long problemId,
                                                     @RequestBody @Valid PracticeSubmitRequest request,
                                                     @LoginUser User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(practiceSubmissionService.submit(problemId, request, user));
    }

    @Operation(summary = "내 연습 제출 이력 - 최신 먼저. 코드 전문은 단건에서")
    @LoginOnly
    @GetMapping("/my")
    public java.util.List<SubmissionSummary> my(@PathVariable Long problemId, @LoginUser User user) {
        return practiceSubmissionService.findMine(problemId, user);
    }

    @Operation(summary = "내 연습 제출 단건 - 코드 전문 + 채점 결과. 남의 제출이면 404")
    @LoginOnly
    @GetMapping("/{submissionId}")
    public SubmissionResponse get(@PathVariable Long problemId, @PathVariable Long submissionId, @LoginUser User user) {
        return practiceSubmissionService.findOne(problemId, submissionId, user);
    }
}
