package kr.haedal.ondal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 마이페이지 활동 요약 (GET /api/me/stats) - 본인 것만, 숫자 4개. 점수·랭킹은 없다(P3 티어 이전) */
public record MyStatsResponse(
        @Schema(description = "계정 생성 시각(UTC) - 첫 로그인 또는 선등록 시각") Instant joinedAt,
        @Schema(description = "분반 과제 제출 건수 - 재제출 포함, 코드·파일·링크 전부") long assignmentSubmissions,
        @Schema(description = "HOJ 연습 제출 건수 - 재제출 포함") long practiceSubmissions,
        @Schema(description = "맞힌 문제 수 - 과제 제출·연습 제출 어느 쪽이든 ACCEPTED 를 한 번이라도 받은 문제") long solvedProblems
) {
}
