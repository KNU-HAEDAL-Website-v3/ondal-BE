package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 제출 코멘트 남기기·덮어쓰기 요청 (PUT). 지우기는 DELETE */
public record SubmissionCommentRequest(
        @Schema(description = "코멘트 내용", example = "입력 처리가 깔끔합니다. 변수명만 조금 더 의미 있게 지어 보세요.")
        @NotBlank(message = "코멘트 내용은 비어 있을 수 없습니다.")
        @Size(max = 5000, message = "코멘트 내용은 5000자 이하여야 합니다.")
        String content
) {
}
