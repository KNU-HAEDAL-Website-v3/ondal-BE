package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** #50 학생용 - 공개 케이스(예시)와 제한. 케이스 0개면 enabled=false, samples 빈 배열 */
public record JudgeSamplesResponse(
        @Schema(description = "자동 채점 문제인가") boolean enabled,
        int timeLimitMs,
        int memoryLimitMb,
        @Schema(description = "자동 채점을 지원하는 언어") List<String> languages,
        List<Sample> samples
) {
    public record Sample(int position, String input, String expectedOutput) {
    }
}
