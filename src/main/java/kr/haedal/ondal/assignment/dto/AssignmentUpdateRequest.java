package kr.haedal.ondal.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * 과제 수정 요청 (PUT 전체 교체) - 필드·검증은 등록 요청과 동일.
 * 배정된 문제를 다른 문제로 바꾸는 것도 허용한다 - 잘못 고른 문제를 과제 삭제(제출 연쇄 삭제) 없이 고칠 수 있어야 한다.
 */
public record AssignmentUpdateRequest(
        @Schema(description = "배정할 문제 id - 바꾸면 그 과제의 문제가 교체된다(기존 제출 기록은 남는다)")
        @NotNull(message = "배정할 문제를 골라야 합니다.")
        Long problemId,

        @Schema(description = "차시 번호 (선택)")
        @Positive(message = "차시 번호는 1 이상이어야 합니다.")
        Integer sessionNo,

        @Schema(description = "마감 시각(UTC)")
        @NotNull(message = "마감 시각은 비어 있을 수 없습니다.")
        Instant dueAt
) {
}
