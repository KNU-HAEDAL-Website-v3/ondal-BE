package kr.haedal.ondal.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * 과제 등록 요청 = "이 문제를 이 분반에 이 마감으로 배정" (V7).
 * 제목·본문·번호·테스트케이스는 문제(Problem)의 것이라 여기 없다 - 새 문제를 내려면 POST /api/problems 를 먼저 부른다.
 */
public record AssignmentCreateRequest(
        @Schema(description = "배정할 문제 id - 라이브러리에서 고르거나(가져오기) 방금 만든 문제의 id", example = "3")
        @NotNull(message = "배정할 문제를 골라야 합니다.")
        Long problemId,

        @Schema(description = "차시 번호 (선택) - 자유 입력, 중복·건너뜀 허용. 차시에 속하지 않는 과제면 생략", example = "1")
        @Positive(message = "차시 번호는 1 이상이어야 합니다.")
        Integer sessionNo,

        @Schema(description = "마감 시각(UTC). 과거 시각도 허용(즉시 마감은 운영 판단). 마감을 수정하면 지각 판정은 새 마감 기준으로 재계산")
        @NotNull(message = "마감 시각은 비어 있을 수 없습니다.")
        Instant dueAt
) {
}
