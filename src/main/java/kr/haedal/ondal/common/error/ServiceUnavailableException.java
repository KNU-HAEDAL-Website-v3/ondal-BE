package kr.haedal.ondal.common.error;

/**
 * 외부 구성 요소(채점 엔진 등)를 지금 쓸 수 없음. → 503 + 호출자가 정한 코드 (예: JUDGE_UNAVAILABLE)
 * 사용: throw new ServiceUnavailableException("JUDGE_UNAVAILABLE", "채점 엔진이 연결되어 있지 않습니다.");
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String code;

    public ServiceUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
