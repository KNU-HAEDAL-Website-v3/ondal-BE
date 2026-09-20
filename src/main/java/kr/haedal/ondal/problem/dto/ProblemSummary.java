package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 문제 목록(HOJ) 행 - 본문은 빼고 목록에 필요한 것만 */
public record ProblemSummary(
        Long id,

        @Schema(description = "문제 번호 - 전역 유일, 1000부터. 표시 형식(#1000)은 FE 몫")
        Integer problemNo,

        String title,

        @Schema(description = "붙은 태그 - 이름순")
        List<TagResponse> tags,

        @Schema(description = "자동 채점 문제인가 = 테스트케이스 1개 이상")
        boolean judgeEnabled,

        @Schema(description = "이 문제가 과제로 배정된 횟수 - 0이면 아직 한 번도 안 낸 문제")
        int assignedCount,

        @Schema(description = "요청자가 이 문제를 맞힌 적이 있는가 - 과제 제출·HOJ 연습 제출 어느 쪽이든. myStatus == SOLVED 와 같다 (하위 호환)")
        boolean solved,

        @Schema(description = "나의 상태 - SOLVED(맞힘) / ATTEMPTED(채점된 제출은 있으나 아직 못 풂) / NONE. 연습·과제 합산")
        ProblemMyStatus myStatus,

        @Schema(description = "이 문제를 푼 사람 수 - ACCEPTED 판정이 있는 사용자 수 (연습·과제 합산)")
        int solvedUserCount,

        @Schema(description = "채점된 제출 수 - judge_results 행 수 (연습·과제 합산)")
        int submissionCount,

        @Schema(description = "ACCEPTED 비율(%) - 소수점 버림. 채점된 제출이 0이면 null")
        Integer acceptedRate,

        @Schema(description = "요청자가 북마크했는가 - PUT/DELETE .../bookmark 로 바꾼다")
        boolean bookmarked,

        @Schema(description = "난이도 1~25 - 표기 \"대분류-소분류\" 는 FE 몫 ((n-1)/5+1 - (n-1)%5+1). null = 미지정")
        Integer difficulty,

        @Schema(description = "제출 허용 언어 - 빈 배열이면 제한 없음")
        List<String> allowedLanguages
) {
}
