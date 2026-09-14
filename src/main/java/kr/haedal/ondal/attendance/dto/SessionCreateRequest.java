package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 차시 등록 요청. sessionNo 를 비우면 자동 채번(현재 최대 + 1, 없으면 1) */
public record SessionCreateRequest(
        @Schema(description = "차시 번호 - 비우면 자동(최대 + 1). 분반 안에서 유일, 중복이면 409", example = "1")
        @Min(value = 1, message = "차시 번호는 1 이상이어야 합니다.")
        Integer sessionNo,

        @Schema(description = "차시 제목 (선택)", example = "포인터와 배열")
        @Size(max = 100, message = "차시 제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "수업 날짜 (KST 달력일)", example = "2026-09-16")
        @NotNull(message = "수업 날짜는 비어 있을 수 없습니다.")
        LocalDate heldOn
) {
}
