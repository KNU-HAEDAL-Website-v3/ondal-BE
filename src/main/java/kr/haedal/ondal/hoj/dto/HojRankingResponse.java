package kr.haedal.ondal.hoj.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;
import java.util.List;

/** 랭킹 (docs hoj/api.md 4절) - 푼 문제 수만, 점수·티어 없음 (PM 결정 13) */
public record HojRankingResponse(
        List<Entry> items,
        @Schema(description = "요청자의 순위 - 푼 문제가 0개면(목록에 없으면) null") Me me
) {
    public record Entry(
            @Schema(description = "순위 - 푼 문제 수가 같으면 같은 순위 (1, 1, 3)") int rank,
            UserSummary user,
            @Schema(description = "푼 문제 수 - 연습·과제 합산") int solvedCount,
            @Schema(description = "연습 제출 수") long submissionCount,
            @Schema(description = "지금의 푼 문제 수에 도달한 시각 - 문제마다 처음 맞힌 제출 시각 중 가장 늦은 것") Instant lastSolvedAt
    ) {
    }

    public record Me(int rank, int solvedCount) {
    }
}
