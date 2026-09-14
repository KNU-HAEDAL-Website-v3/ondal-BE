package kr.haedal.ondal.judge.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * ondal.judge.* - 채점 기본값·상한·언어 매핑·Judge0 연결 (application.yml).
 * 제한 기본값·상한은 응답(JudgeConfigResponse)으로 FE 에도 내려 폼이 하드코딩하지 않게 한다.
 */
@ConfigurationProperties(prefix = "ondal.judge")
public record JudgeProperties(
        String engine,
        boolean async,
        int defaultTimeLimitMs,
        int defaultMemoryLimitMb,
        int maxTimeLimitMs,
        int maxMemoryLimitMb,
        int maxTestCases,
        int maxRunInputs,
        /** FE 언어 문자열 → Judge0 language_id. 키에 공백·기호가 있어 yml 에서는 "[Python 3]" 형태 */
        Map<String, Integer> languages,
        Judge0 judge0
) {
    public static final int MIN_TIME_LIMIT_MS = 100;
    public static final int MIN_MEMORY_LIMIT_MB = 16;

    public record Judge0(String url, String token, int batchSize, int pollIntervalMs, int pollTimeoutMs, int maxAttempts) {
    }

    public int effectiveTimeLimitMs(Integer perAssignment) {
        return perAssignment == null ? defaultTimeLimitMs : perAssignment;
    }

    public int effectiveMemoryLimitMb(Integer perAssignment) {
        return perAssignment == null ? defaultMemoryLimitMb : perAssignment;
    }

    public boolean supportsLanguage(String language) {
        return language != null && languages != null && languages.containsKey(language);
    }
}
