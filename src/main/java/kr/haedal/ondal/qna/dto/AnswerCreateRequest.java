package kr.haedal.ondal.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 답변 등록 요청 */
public record AnswerCreateRequest(
        @Schema(description = "답변 내용 - 자유 텍스트")
        @NotBlank(message = "답변 내용은 비어 있을 수 없습니다.")
        @Size(max = 10000, message = "답변 내용은 10000자 이하여야 합니다.")
        String content
) {
}
