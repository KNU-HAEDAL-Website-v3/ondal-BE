package kr.haedal.ondal.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 등록 요청 - 전체 공지(POST /api/notices)와 분반 공지(POST /api/cohorts/{cohortId}/notices)가 같은 본문을 쓴다. 대상은 경로가 정한다 */
public record NoticeCreateRequest(
        @Schema(description = "공지 제목", example = "2026-2 부트캠프 운영 안내")
        @NotBlank(message = "공지 제목은 비어 있을 수 없습니다.")
        @Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "공지 내용 - 자유 텍스트")
        @NotBlank(message = "공지 내용은 비어 있을 수 없습니다.")
        @Size(max = 10000, message = "공지 내용은 10000자 이하여야 합니다.")
        String content,

        @Schema(description = "필독 - 목록 최상단 고정. 생략하면 false")
        Boolean pinned
) {
    public boolean pinnedOrFalse() {
        return Boolean.TRUE.equals(pinned);
    }
}
