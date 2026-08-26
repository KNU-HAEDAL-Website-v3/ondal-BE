package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.submission.entity.Submission;

import java.time.Instant;

/** 내 제출 이력의 행 (#19) - 코드 전문(codeText)은 뺀다. 이력 N건에 코드 전문을 다 실으면 응답이 무거워짐 - 코드 확인은 #20 단건 */
public record SubmissionSummary(
        Long id,
        String language,
        String fileName,
        Long fileSize,
        String linkUrl,
        Instant submittedAt,

        @Schema(description = "지각 여부 - 서버 판정값. 프론트 재계산 금지")
        boolean late
) {
    public static SubmissionSummary of(Submission submission, Instant dueAt) {
        return new SubmissionSummary(
                submission.getId(),
                submission.getLanguage(),
                submission.getFileName(),
                submission.getFileSize(),
                submission.getLinkUrl(),
                submission.getSubmittedAt(),
                submission.getSubmittedAt().isAfter(dueAt)
        );
    }
}
