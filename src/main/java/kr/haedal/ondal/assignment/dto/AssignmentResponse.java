package kr.haedal.ondal.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.assignment.entity.Assignment;

import java.time.Instant;

/** 과제 응답 - 목록·단건·등록·수정 응답이 전부 이 하나의 모양이다. 제출 상태 필드는 제출 슬라이스에서 확장. */
public record AssignmentResponse(
        Long id,

        @Schema(description = "차시 번호 - 차시에 속하지 않는 과제는 null. 목록은 차시 오름차순(null 마지막) → 등록순")
        Integer sessionNo,

        String title,

        @Schema(description = "과제 내용 - 문제 링크를 포함한 자유 텍스트 (선택)")
        String description,

        @Schema(description = "마감 시각(UTC) - KST 변환은 프론트 몫. 마감이 수정되면 지각 판정도 새 마감 기준으로 재계산된다")
        Instant dueAt,

        Instant createdAt
) {
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getSessionNo(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getDueAt(),
                assignment.getCreatedAt()
        );
    }
}
