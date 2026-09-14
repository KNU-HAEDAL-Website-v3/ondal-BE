package kr.haedal.ondal.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.qna.entity.Answer;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/** 답변 응답 - 목록·등록·수정 응답이 전부 이 하나. canEdit·canDelete 는 질문과 같은 규칙(작성자 / 작성자 또는 운영진 이상, 분반 ACTIVE) */
public record AnswerResponse(
        Long id,
        String content,
        @Schema(description = "작성자 - id·이름·직책 명칭만 (UserSummary)") UserSummary author,
        Instant createdAt,
        @Schema(description = "요청자가 수정할 수 있는가 - 작성자 본인이고 분반이 ACTIVE") boolean canEdit,
        @Schema(description = "요청자가 삭제할 수 있는가 - 작성자 본인 또는 운영진 이상이고 분반이 ACTIVE") boolean canDelete
) {
    public static AnswerResponse of(Answer answer, UserSummary author, boolean canEdit, boolean canDelete) {
        return new AnswerResponse(answer.getId(), answer.getContent(), author, answer.getCreatedAt(), canEdit, canDelete);
    }
}
