package kr.haedal.ondal.hoj.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.problem.dto.TagResponse;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.submission.dto.LanguageCount;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;
import java.util.List;

/**
 * 사용자 페이지 (docs hoj/api.md 3절) - 누구나 누구의 페이지든 열람. 이름·활동만, loginId 없음.
 * "푼 문제" 계열(solvedCount·attemptedCount·solvedProblems·attemptedProblems·tagStats)은 연습·과제 합산,
 * 제출 계열(submissionCount·acceptedCount·acceptedRate·languages·activity·recentSubmissions)은 연습 제출만
 */
public record HojUserPageResponse(
        UserSummary user,
        @Schema(description = "계정 생성 시각(UTC)") Instant joinedAt,
        @Schema(description = "전체 랭킹 순위 - 푼 문제 0개면 null") Integer rank,
        Stats stats,
        @Schema(description = "언어별 연습 제출 수 - 많이 쓴 언어 먼저") List<LanguageCount> languages,
        @Schema(description = "푼 문제 - 번호순") List<ProblemBrief> solvedProblems,
        @Schema(description = "시도 중인 문제(채점된 제출은 있으나 아직 못 풂) - 번호순") List<ProblemBrief> attemptedProblems,
        @Schema(description = "태그별 숙련도 - 태그 이름순, 문제가 없는 태그는 생략") List<TagStat> tagStats,
        @Schema(description = "활동 잔디 - 최근 365일, KST 날짜 기준, 제출 0인 날은 생략") List<ActivityDay> activity,
        @Schema(description = "최근 연습 제출 20건 - 채점 현황 피드의 행과 같은 모양") List<HojSubmissionItem> recentSubmissions
) {
    public record Stats(
            @Schema(description = "푼 문제 수 (연습·과제 합산)") int solvedCount,
            @Schema(description = "시도 중인 문제 수") int attemptedCount,
            @Schema(description = "연습 제출 수 (재제출 포함)") long submissionCount,
            @Schema(description = "연습 제출 중 ACCEPTED 수") long acceptedCount,
            @Schema(description = "ACCEPTED 비율(%) - 소수점 버림. 연습 제출이 0이면 null") Integer acceptedRate
    ) {
    }

    public record ProblemBrief(Long id, Integer problemNo, String title, Integer difficulty) {
        public static ProblemBrief from(Problem problem) {
            return new ProblemBrief(problem.getId(), problem.getProblemNo(), problem.getTitle(), problem.getDifficulty());
        }
    }

    public record TagStat(
            TagResponse tag,
            @Schema(description = "이 태그가 붙은 문제 중 푼 수") int solved,
            @Schema(description = "이 태그가 붙은 문제 수") int total
    ) {
    }

    public record ActivityDay(
            @Schema(description = "KST 날짜 (YYYY-MM-DD)", example = "2026-09-19") String date,
            int count
    ) {
    }
}
