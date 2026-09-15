package kr.haedal.ondal.judge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.judge.dto.RejudgeResponse;
import kr.haedal.ondal.judge.service.JudgeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 과제 스코프 채점 API (#51) - 재채점만 남는다.
 *
 * 채점 설정·테스트케이스·출제 도구·공개 예시는 V7 에서 문제 스코프(ProblemJudgeController)로 옮겼다:
 * 채점 기준은 문제의 것이고, 같은 문제를 쓰는 분반들이 하나를 공유해야 기준이 갈라지지 않는다.
 * 재채점만 여기 남긴 이유는 "내 반 제출만 다시 돌린다"가 분반 운영 동작이기 때문.
 *
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

    @Operation(summary = "[운영진] 재채점 - 이 과제의 코드 제출 전부. 테스트케이스 0개·보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PostMapping("/rejudge")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RejudgeResponse rejudge(@PathVariable Long cohortId, @PathVariable Long assignmentId) {
        return judgeService.rejudge(cohortId, assignmentId);
    }
}
