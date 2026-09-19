package kr.haedal.ondal.common.error;

/**
 * 문제 은행 레포(GitHub)에서 가져오기 실패 - 토큰 만료·레포 이름 오류·네트워크·zip 형식 오류 등. 502 PROBLEM_BANK_FETCH_FAILED.
 * 메시지는 관리자 화면에 그대로 보이므로 원인을 구체적으로(어느 요청이 몇 번 응답을 줬는지) 담는다. 토큰 값은 절대 넣지 않는다.
 */
public class ProblemBankFetchException extends RuntimeException {

    public ProblemBankFetchException(String message) {
        super(message);
    }

    public ProblemBankFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
