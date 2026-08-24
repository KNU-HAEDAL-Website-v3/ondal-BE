package kr.haedal.ondal.common.error;

/**
 * Bean Validation 밖에서 발견한 잘못된 입력 (예: 경로 변수 {cohortId}가 숫자가 아님). → 400 INVALID_INPUT
 * 컨트롤러 파라미터 바인딩보다 먼저 도는 인터셉터가 주로 사용한다.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
