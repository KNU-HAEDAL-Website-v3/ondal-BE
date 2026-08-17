package kr.haedal.hoj.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "loginId는 비어 있을 수 없습니다.")
        @Size(max = 50, message = "loginId는 50자 이하여야 합니다.")   // users.login_id varchar(50)
        String loginId
) {
}
