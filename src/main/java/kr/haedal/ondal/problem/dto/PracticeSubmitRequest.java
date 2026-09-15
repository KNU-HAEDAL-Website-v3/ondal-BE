package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HOJ 연습 제출 요청 - 코드만 받는다 (마감·파일·링크 없음) */
public record PracticeSubmitRequest(
        @Schema(description = "제출 코드")
        @NotBlank(message = "코드는 비어 있을 수 없습니다.")
        @Size(max = 100000, message = "코드는 100000자 이하여야 합니다.")
        String codeText,

        @Schema(description = "제출 언어 - 서버 설정(ondal.judge.languages)에 있는 값이어야 채점된다", example = "Python 3")
        @NotBlank(message = "언어를 선택해야 합니다.")
        @Size(max = 30, message = "언어는 30자 이하여야 합니다.")
        String language
) {
}
