package kr.haedal.ondal.judge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.judge.dto.JudgeRunResponse;
import kr.haedal.ondal.judge.dto.ProblemRunRequest;
import kr.haedal.ondal.judge.service.ProblemRunService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "내 입력으로 실행" API (docs hoj/api.md 8절) - 로그인한 누구나. 출제 도구(.../judge/run, 운영진)와 같은 엔진·같은 응답 모양이지만
 * 입력 수·크기가 작고 판정이 없으며 사용자당 분당 10회로 묶는다.
 */
@Tag(name = "Judge")
@RestController
@RequestMapping("/api/problems/{problemId}/run")
public class ProblemRunController {

    private final ProblemRunService problemRunService;

    public ProblemRunController(ProblemRunService problemRunService) {
        this.problemRunService = problemRunService;
    }

    @Operation(summary = "내 입력으로 실행 - 입력 1~5개에 코드를 돌려 출력만 돌려준다(저장·판정 없음). 문제의 허용 언어 밖 400, 사용자당 분당 10회 초과 429 TOO_MANY_REQUESTS, 엔진 미연결 503 JUDGE_UNAVAILABLE")
    @LoginOnly
    @PostMapping
    public JudgeRunResponse run(@PathVariable Long problemId, @RequestBody @Valid ProblemRunRequest request, @LoginUser User user) {
        return problemRunService.run(problemId, request, user);
    }
}
