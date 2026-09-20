package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/** 다른 사람 풀이 1건 (docs hoj/api.md 5절) - 연습 제출 중 ACCEPTED, 사용자당 최신 1건. 요청자 본인 것은 없다 */
public record AcceptedSolutionResponse(
        Long submissionId,
        @Schema(description = "푼 사람 - id·이름·직책만 (loginId 없음)") UserSummary user,
        String language,
        String codeText,
        Instant submittedAt,
        @Schema(description = "케이스 중 최대 실행 시간(ms)") Integer maxTimeMs,
        @Schema(description = "케이스 중 최대 메모리(KB)") Integer maxMemoryKb
) {
    /** submission.user 가 fetch join 돼 있어야 한다 (findLatestAcceptedPerUser) */
    public static AcceptedSolutionResponse of(Submission submission, JudgeResult judge) {
        return new AcceptedSolutionResponse(
                submission.getId(),
                UserSummary.of(submission.getUser(), null),   // HOJ 는 분반 문맥이 없다 - 직책은 전역 역할로만
                submission.getLanguage(),
                submission.getCodeText(),
                submission.getSubmittedAt(),
                judge == null ? null : judge.getMaxTimeMs(),
                judge == null ? null : judge.getMaxMemoryKb());
    }
}
