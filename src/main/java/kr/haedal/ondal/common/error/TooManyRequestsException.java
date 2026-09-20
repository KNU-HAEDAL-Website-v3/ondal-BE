package kr.haedal.ondal.common.error;

/**
 * 사용자별 호출 한도 초과 (예: 내 입력으로 실행 - 분당 10회). → 429 TOO_MANY_REQUESTS (docs hoj/api.md 8절·9절)
 * 사용: throw new TooManyRequestsException("실행은 1분에 10번까지예요. 잠시 뒤 다시 시도하세요.");
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
