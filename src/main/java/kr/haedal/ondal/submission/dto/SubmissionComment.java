package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/** 제출에 달린 운영진 코멘트 - SubmissionResponse.comment. 코멘트가 없으면 응답 필드 자체가 null */
public record SubmissionComment(
        @Schema(description = "코멘트 내용 - 자유 텍스트") String content,
        @Schema(description = "마지막으로 코멘트를 남긴(수정한) 운영진 - id·이름·직책") UserSummary author,
        @Schema(description = "마지막 변경 시각(UTC)") Instant commentedAt
) {
}
