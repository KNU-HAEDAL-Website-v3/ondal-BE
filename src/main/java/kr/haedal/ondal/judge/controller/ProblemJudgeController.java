package kr.haedal.ondal.judge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.auth.authorization.OperatorAnywhere;
import kr.haedal.ondal.judge.dto.JudgeConfigRequest;
import kr.haedal.ondal.judge.dto.JudgeConfigResponse;
import kr.haedal.ondal.judge.dto.JudgeRunRequest;
import kr.haedal.ondal.judge.dto.JudgeRunResponse;
import kr.haedal.ondal.judge.dto.JudgeSamplesResponse;
import kr.haedal.ondal.judge.service.JudgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문제 스코프 채점 API (#47·#48·#49·#50) - 채점 기준은 문제(Problem)의 것이다 (V7).
 *
 * 같은 문제를 여러 분반에 배정해도 테스트케이스·실행 제한은 하나를 공유한다 - 복제하지 않으므로 기준이 갈라지지 않는다.
 * 대신 케이스를 고치면 그 문제로 채점된 제출 전부가 재채점 대상이 된다(PUT 의 rejudge 플래그).
 */
@Tag(name = "Judge")
@RestController
@RequestMapping("/api/problems/{problemId}/judge")
public class ProblemJudgeController {

    private final JudgeService judgeService;

    public ProblemJudgeController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    @Operation(summary = "[운영진] 채점 설정·테스트케이스 조회 - 비공개 케이스 포함, 폼 기본값·상한·지원 언어·재채점 대상 건수")
    @OperatorAnywhere
    @GetMapping
    public JudgeConfigResponse getConfig(@PathVariable Long problemId) {
        return judgeService.getConfig(problemId);
    }

    @Operation(summary = "[운영진] 채점 설정·테스트케이스 저장(통째 교체) - 케이스 0개 = 자동 채점 해제. rejudge=true 면 이 문제의 코드 제출 전부 재채점")
    @OperatorAnywhere
    @PutMapping
    public JudgeConfigResponse saveConfig(@PathVariable Long problemId,
                                          @RequestBody @Valid JudgeConfigRequest request) {
        return judgeService.saveConfig(problemId, request);
    }

    @Operation(summary = "[운영진] 출제 도구 실행 - 정답 코드를 입력들에 실행(저장 없음). expectedOutputs 가 있으면 판정까지. 엔진 미연결 503")
    @OperatorAnywhere
    @PostMapping("/run")
    public JudgeRunResponse run(@PathVariable Long problemId, @RequestBody @Valid JudgeRunRequest request) {
        return judgeService.run(problemId, request);
    }

    @Operation(summary = "공개 케이스(예시)·제한·지원 언어 - 과제 상세와 HOJ 문제 화면이 같이 쓴다")
    @LoginOnly
    @GetMapping("/samples")
    public JudgeSamplesResponse samples(@PathVariable Long problemId) {
        return judgeService.samples(problemId);
    }
}
