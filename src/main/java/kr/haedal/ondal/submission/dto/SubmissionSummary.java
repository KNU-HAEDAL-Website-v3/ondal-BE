package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;

import java.time.Instant;
import java.util.List;

/** 내 제출 이력의 행 (#19) - 코드 전문(codeText)은 뺀다. 이력 N건에 코드 전문을 다 실으면 응답이 무거워짐 - 코드 확인은 #20 단건 */
public record SubmissionSummary(
        Long id,

        @Schema(description = "제출 형태 - CODE / FILE / LINK")
        SubmissionType type,

        String language,
        String fileName,
        Long fileSize,

        @Schema(description = "링크 URL 목록 - position 순. LINK 외 형태는 빈 배열")
        List<String> links,

        Instant submittedAt,

        @Schema(description = "지각 여부 - 서버 판정값. 프론트 재계산 금지")
        boolean late
) {
    public static SubmissionSummary of(Submission submission, Instant dueAt) {
        return new SubmissionSummary(
                submission.getId(),
                submission.getType(),
                submission.getLanguage(),
                submission.getFileName(),
                submission.getFileSize(),
                submission.getLinkUrls(),
                submission.getSubmittedAt(),
                submission.getSubmittedAt().isAfter(dueAt)
        );
    }
}
