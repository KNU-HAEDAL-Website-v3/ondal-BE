package kr.haedal.ondal.hoj.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.JudgeStatus;
import kr.haedal.ondal.judge.entity.Verdict;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/** 채점 현황 피드의 행 (docs hoj/api.md 2절) - 사용자 페이지의 recentSubmissions 도 같은 모양. codeText 는 싣지 않는다 */
public record HojSubmissionItem(
        Long id,
        HojProblemRef problem,
        @Schema(description = "제출자 - id·이름·직책만 (loginId 없음)") UserSummary user,
        String language,
        @Schema(description = "채점 상태 - 채점 대상이 아니었다면 null") JudgeStatus judgeStatus,
        @Schema(description = "판정 - 채점 중(PENDING/RUNNING)이면 null") Verdict verdict,
        Integer passedCases,
        Integer totalCases,
        Integer maxTimeMs,
        Integer maxMemoryKb,
        Instant submittedAt
) {
    /** submission.problem·user 가 fetch join 돼 있어야 한다 (findPracticeFeed) */
    public static HojSubmissionItem of(Submission submission, JudgeResult judge) {
        return new HojSubmissionItem(
                submission.getId(),
                HojProblemRef.from(submission.getProblem()),
                UserSummary.of(submission.getUser(), null),   // HOJ 는 분반 문맥이 없다 - 직책은 전역 역할로만
                submission.getLanguage(),
                judge == null ? null : judge.getStatus(),
                judge == null ? null : judge.getVerdict(),
                judge == null ? null : judge.getPassedCases(),
                judge == null ? null : judge.getTotalCases(),
                judge == null ? null : judge.getMaxTimeMs(),
                judge == null ? null : judge.getMaxMemoryKb(),
                submission.getSubmittedAt());
    }
}
