package kr.haedal.hoj.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "loginId는 비어 있을 수 없습니다.")
        String loginId
) {
}
