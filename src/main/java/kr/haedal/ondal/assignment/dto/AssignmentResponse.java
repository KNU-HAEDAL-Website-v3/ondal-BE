package kr.haedal.ondal.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.submission.entity.SubmissionStatus;

import java.time.Instant;

/**
 * 과제 응답 - 목록·단건·등록·수정 응답이 전부 이 하나의 모양이다.
 * myStatus·submissionCount는 요청자에 따라 달라지므로 AssignmentResponseAssembler가 채운다.
 */
public record AssignmentResponse(
        Long id,

        @Schema(description = "차시 번호 - 차시에 속하지 않는 과제는 null. 목록은 차시 오름차순(null 마지막) → 등록순")
        Integer sessionNo,

        String title,

        @Schema(description = "과제 내용 - 문제 링크를 포함한 자유 텍스트 (선택)")
        String description,

        @Schema(description = "마감 시각(UTC) - KST 변환은 프론트 몫. 마감이 수정되면 지각 판정도 새 마감 기준으로 재계산된다")
        Instant dueAt,

        Instant createdAt,

        @Schema(description = "요청자 본인의 제출 상태 - NOT_SUBMITTED/SUBMITTED/SUBMITTED_EXTRA/LATE. 서버 판정값(프론트 재계산 금지). 분반 비소속(비소속 관리자)이면 null")
        SubmissionStatus myStatus,

        @Schema(description = "제출 이력 총 건수 - 운영진·관리자에게만 값, 수강생은 null. 과제 삭제 확인 창의 \"제출물 N건 삭제\" 경고가 이 값을 쓴다")
        Integer submissionCount
) {
    public static AssignmentResponse of(Assignment assignment, SubmissionStatus myStatus, Integer submissionCount) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getSessionNo(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getDueAt(),
                assignment.getCreatedAt(),
                myStatus,
                submissionCount
        );
    }
}
