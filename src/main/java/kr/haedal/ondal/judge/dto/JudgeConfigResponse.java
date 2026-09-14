package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** #47·#48 응답 - 설정 + 케이스 전체(비공개 포함, 운영진 전용) + 폼 안내용 기본값·상한 + 재채점 정보 */
public record JudgeConfigResponse(
        @Schema(description = "자동 채점 문제인가 = 테스트케이스 1개 이상") boolean enabled,
        @Schema(description = "채점 엔진이 연결돼 있는가 - false 면 저장은 되지만 제출은 채점 대기(PENDING)에 머문다") boolean engineAvailable,
        @Schema(description = "적용 중인 시간 제한(ms) - 과제 값이 없으면 기본값") int timeLimitMs,
        @Schema(description = "적용 중인 메모리 제한(MB)") int memoryLimitMb,
        int defaultTimeLimitMs,
        int defaultMemoryLimitMb,
        int maxTimeLimitMs,
        int maxMemoryLimitMb,
        int maxTestCases,
        @Schema(description = "자동 채점을 지원하는 언어(FE 셀렉트 문자열) - 이 밖의 언어로는 자동 채점 문제에 제출할 수 없다") List<String> languages,
        List<TestCaseResponse> testCases,
        @Schema(description = "이 과제의 코드 제출 건수 = 재채점 대상") int affectedSubmissions,
        @Schema(description = "#48 에서 실제로 재채점 큐에 넣은 건수 (조회·rejudge=false 면 0)") int rejudgeQueued
) {
}
