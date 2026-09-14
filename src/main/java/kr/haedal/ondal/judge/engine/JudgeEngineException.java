package kr.haedal.ondal.judge.engine;

/**
 * 엔진 호출 실패. retryable = 일시 장애(연결 실패·5xx·큐 초과) → 워커가 백오프 후 재시도.
 * retryable 이 아니면(인증 실패·요청 형식 오류) 즉시 JUDGE_ERROR - 설정 문제이므로 로그로 드러낸다.
 */
public class JudgeEngineException extends RuntimeException {

    private final boolean retryable;

    public JudgeEngineException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public JudgeEngineException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
