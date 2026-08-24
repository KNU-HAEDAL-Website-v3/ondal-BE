package kr.haedal.ondal.common.error;

/**
 * 로그인은 했지만 권한이 부족한 요청. → 403 FORBIDDEN
 * (분반 스코프 4단계 판정 컴포넌트가 다음 단계에서 이 예외를 사용한다)
 */
public class ForbiddenException extends RuntimeException {
}
