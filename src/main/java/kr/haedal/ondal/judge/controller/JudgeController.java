package kr.haedal.ondal.judge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.judge.dto.JudgeConfigRequest;
import kr.haedal.ondal.judge.dto.JudgeConfigResponse;
import kr.haedal.ondal.judge.dto.JudgeRunRequest;
import kr.haedal.ondal.judge.dto.JudgeRunResponse;
import kr.haedal.ondal.judge.dto.JudgeSamplesResponse;
import kr.haedal.ondal.judge.dto.RejudgeResponse;
import kr.haedal.ondal.judge.service.JudgeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자동 채점 API (#47~#51) - 채점 설정·테스트케이스(운영진), 출제 도구 실행(운영진), 공개 예시(소속 누구나), 재채점(운영진).
 * 채점 결과 조회 API 는 없다 - 제출 응답(#18·#19·#20·#22)에 실린다 (docs judge/api.md 2절).
 */
@Tag(name = "Judge")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/assignments/{assignmentId}/judge")
public class JudgeController {

    private final JudgeService judgeService;

    public JudgeController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    @Operation(summary = "[운영진] 채점 설정·테스트케이스 조회 - 비공개 케이스 포함, 폼 기본값·상한·지원 언어·재채점 대상 건수")
    @CohortRole(EnrollmentRole.OPERATOR)
    @GetMapping
    public JudgeConfigResponse getConfig(@PathVariable Long cohortId, @PathVariable Long assignmentId) {
        return judgeService.getConfig(cohortId, assignmentId);
    }

    @Operation(summary = "[운영진] 채점 설정·테스트케이스 저장(통째 교체) - 케이스 0개 = 자동 채점 해제. rejudge=true 면 기존 코드 제출 재채점. 보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PutMapping
    public JudgeConfigResponse saveConfig(@PathVariable Long cohortId, @PathVariable Long assignmentId,
                                          @RequestBody @Valid JudgeConfigRequest request) {
        return judgeService.saveConfig(cohortId, assignmentId, request);
    }

    @Operation(summary = "[운영진] 출제 도구 실행 - 정답 코드를 입력들에 실행(저장 없음). expectedOutputs 가 있으면 판정까지. 엔진 미연결 503")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PostMapping("/run")
    public JudgeRunResponse run(@PathVariable Long cohortId, @PathVariable Long assignmentId,
                                @RequestBody @Valid JudgeRunRequest request) {
        return judgeService.run(cohortId, assignmentId, request);
    }

    @Operation(summary = "공개 케이스(예시)·제한·지원 언어 - 학생 과제 상세의 예시 절")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/samples")
    public JudgeSamplesResponse samples(@PathVariable Long cohortId, @PathVariable Long assignmentId) {
        return judgeService.samples(cohortId, assignmentId);
    }

    @Operation(summary = "[운영진] 재채점 - 이 과제의 코드 제출 전부. 테스트케이스 0개·보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PostMapping("/rejudge")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RejudgeResponse rejudge(@PathVariable Long cohortId, @PathVariable Long assignmentId) {
        return judgeService.rejudge(cohortId, assignmentId);
    }
}
