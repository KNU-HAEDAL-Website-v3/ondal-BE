package kr.haedal.ondal.cohort.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 분반 수정 요청 - PUT 전체 교체. 운영진 변경은 별도 API(/operators)로. */
public record CohortUpdateRequest(
        @Schema(description = "분반 이름", example = "2026-2 C언어")
        @NotBlank(message = "분반 이름은 비어 있을 수 없습니다.")
        @Size(max = 100, message = "분반 이름은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "설명 (선택)")
        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.")
        String description
) {
}
