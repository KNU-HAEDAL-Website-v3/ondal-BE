package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 차시 수정 요청 (PUT 전체 교체) - 번호도 바꿀 수 있다(다른 차시와 중복이면 409). 분반은 바꿀 수 없다 */
public record SessionUpdateRequest(
        @Schema(description = "차시 번호 - 분반 안에서 유일")
        @NotNull(message = "차시 번호는 비어 있을 수 없습니다.")
        @Min(value = 1, message = "차시 번호는 1 이상이어야 합니다.")
        Integer sessionNo,

        @Schema(description = "차시 제목 (선택)")
        @Size(max = 100, message = "차시 제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "수업 날짜 (KST 달력일)")
        @NotNull(message = "수업 날짜는 비어 있을 수 없습니다.")
        LocalDate heldOn
) {
}
