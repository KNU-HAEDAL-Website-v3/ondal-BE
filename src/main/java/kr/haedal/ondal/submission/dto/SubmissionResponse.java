package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/** 제출 단건 응답 (#18 생성·#20 상세) - 코드 전문 포함. 이력 목록은 SubmissionSummary */
public record SubmissionResponse(
        Long id,

        @Schema(description = "제출자 - 최소 정보(id·이름·직책)만")
        UserSummary user,

        String codeText,
        String language,
        String fileName,
        Long fileSize,
        String linkUrl,

        @Schema(description = "제출 시각(UTC) = 서버 수신 시각 - KST 변환은 프론트 몫")
        Instant submittedAt,

        @Schema(description = "지각 여부 - 서버 판정값(submittedAt > dueAt). 프론트 재계산 금지. 마감이 수정되면 재조회 시 값이 바뀔 수 있다")
        boolean late
) {
    public static SubmissionResponse of(Submission submission, Instant dueAt, UserSummary user) {
        return new SubmissionResponse(
                submission.getId(),
                user,
                submission.getCodeText(),
                submission.getLanguage(),
                submission.getFileName(),
                submission.getFileSize(),
                submission.getLinkUrl(),
                submission.getSubmittedAt(),
                submission.getSubmittedAt().isAfter(dueAt)
        );
    }
}
