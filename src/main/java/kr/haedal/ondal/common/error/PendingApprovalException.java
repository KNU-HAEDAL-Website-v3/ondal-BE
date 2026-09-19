package kr.haedal.ondal.common.error;

/**
 * 로그인은 했지만 아직 운영진 승인 전(UserStatus.PENDING)인 계정의 요청. → 403 USER_PENDING
 * FORBIDDEN(권한 부족)과 코드를 나누는 이유: FE 가 403 FORBIDDEN 은 홈으로 보내지만, 이 경우는 "승인 대기" 화면을 보여야 한다.
 */
public class PendingApprovalException extends RuntimeException {
}
