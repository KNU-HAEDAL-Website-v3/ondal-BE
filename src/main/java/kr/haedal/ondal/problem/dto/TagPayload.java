package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 태그 등록·수정 요청 (관리자) - 이름 하나가 전부 */
public record TagPayload(
        @Schema(description = "태그 이름 - 중복이면 409. 표기 흔들림을 막으려고 관리자만 만들 수 있다", example = "다이나믹 프로그래밍")
        @NotBlank(message = "태그 이름은 비어 있을 수 없습니다.")
        @Size(max = 40, message = "태그 이름은 40자 이하여야 합니다.")
        String name
) {
}
