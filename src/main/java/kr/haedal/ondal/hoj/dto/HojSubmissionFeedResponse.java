package kr.haedal.ondal.hoj.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 채점 현황 피드 응답 - 커서 방식 (id desc). 다음 페이지는 beforeId=nextBeforeId 로 */
public record HojSubmissionFeedResponse(
        List<HojSubmissionItem> items,
        @Schema(description = "다음 페이지 커서 = 마지막 항목 id. 더 없으면 null") Long nextBeforeId
) {
}
