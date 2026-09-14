package kr.haedal.ondal.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.notice.entity.Notice;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/**
 * 공지 응답 - 목록·단건·등록·수정 응답이 전부 이 하나의 모양이다.
 * cohort 가 null 이면 전체 공지. author 의 직책과 canEdit·canDelete 는 요청자·분반 상태에 따라 달라지므로 NoticeResponseAssembler 가 채운다.
 */
public record NoticeResponse(
        Long id,
        String title,
        String content,

        @Schema(description = "필독 - 목록 최상단 고정")
        boolean pinned,

        @Schema(description = "분반 공지면 대상 분반(id·이름), 전체 공지면 null")
        NoticeCohortSummary cohort,

        @Schema(description = "작성자 - id·이름·직책 명칭만 (UserSummary). 전체 공지 작성자(관리자)는 해구르르")
        UserSummary author,

        Instant createdAt,

        @Schema(description = "요청자가 이 공지를 수정할 수 있는가 - 전체 공지: 관리자 / 분반 공지: 분반 ACTIVE 이고 그 분반 운영진 이상(관리자 포함)")
        boolean canEdit,

        @Schema(description = "요청자가 이 공지를 삭제할 수 있는가 - canEdit 과 같은 규칙 (공지는 관리 권한이 곧 수정·삭제 권한)")
        boolean canDelete
) {
    /** 대상 분반 요약 - 목록 행의 "대상" 표시용 */
    public record NoticeCohortSummary(Long id, String name) {
    }

    /** 여러 값을 조합하므로 from(entity)가 아니라 of(...) */
    public static NoticeResponse of(Notice notice, UserSummary author, boolean canManage) {
        NoticeCohortSummary cohort = notice.isGlobal()
                ? null
                : new NoticeCohortSummary(notice.getCohort().getId(), notice.getCohort().getName());
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                cohort,
                author,
                notice.getCreatedAt(),
                canManage,
                canManage
        );
    }
}
