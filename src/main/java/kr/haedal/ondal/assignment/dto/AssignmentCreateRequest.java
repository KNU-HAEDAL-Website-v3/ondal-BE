package kr.haedal.ondal.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 과제 등록 요청. 검증 어노테이션은 요청 DTO 필드에만 단다. */
public record AssignmentCreateRequest(
        @Schema(description = "문제 번호 (선택) - 비우면 자동 채번(현재 최대+1, 1000 시작). 지정 시 전역 유일, 중복이면 409", example = "1003")
        @Min(value = 1000, message = "문제 번호는 1000 이상이어야 합니다.")
        Integer problemNo,

        @Schema(description = "차시 번호 (선택) - 자유 입력, 중복·건너뜀 허용. 차시에 속하지 않는 과제면 생략", example = "1")
        @Positive(message = "차시 번호는 1 이상이어야 합니다.")
        Integer sessionNo,

        @Schema(description = "과제 제목", example = "1차시 - 입출력 연습")
        @NotBlank(message = "과제 제목은 비어 있을 수 없습니다.")
        @Size(max = 200, message = "과제 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "과제 내용 (선택) - 문제 링크를 포함한 자유 텍스트")
        @Size(max = 10000, message = "과제 내용은 10000자 이하여야 합니다.")
        String description,

        @Schema(description = "마감 시각(UTC). 과거 시각도 허용(즉시 마감은 운영 판단). 마감을 수정하면 지각 판정은 새 마감 기준으로 재계산")
        @NotNull(message = "마감 시각은 비어 있을 수 없습니다.")
        Instant dueAt
) {
}
