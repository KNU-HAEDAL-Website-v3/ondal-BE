package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.problem.dto.AcceptedSolutionResponse;
import kr.haedal.ondal.problem.service.PracticeSubmissionService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 다른 사람 풀이 API (docs hoj/api.md 5절) - 그 문제를 맞힌 사람과 운영진 이상만 (PM 결정: 표절 우려로 맞힌 뒤에만).
 * 못 푼 사람은 403 NOT_SOLVED - FE 는 홈으로 보내지 않고 "먼저 맞히면 볼 수 있어요" 를 그 자리에 띄운다.
 */
@Tag(name = "Problem", description = "다른 사람 풀이 - 맞힌 사람·운영진만, 연습 제출의 ACCEPTED 를 사용자당 최신 1건")
@RestController
@RequestMapping("/api/problems/{problemId}/accepted-solutions")
public class AcceptedSolutionController {

    private final PracticeSubmissionService practiceSubmissionService;

    public AcceptedSolutionController(PracticeSubmissionService practiceSubmissionService) {
        this.practiceSubmissionService = practiceSubmissionService;
    }

    @Operation(summary = "다른 사람 풀이 - 요청자가 이 문제를 맞혔거나 운영진 이상이어야 한다(아니면 403 NOT_SOLVED). 연습 제출 중 ACCEPTED 를 사용자당 최신 1건, 본인 제외, 최신 먼저, 최대 50건. language 로 거르기")
    @LoginOnly
    @GetMapping
    public List<AcceptedSolutionResponse> list(@PathVariable Long problemId,
                                               @RequestParam(required = false) String language,
                                               @LoginUser User user) {
        return practiceSubmissionService.findAcceptedSolutions(problemId, language, user);
    }
}
