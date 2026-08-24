package kr.haedal.ondal.common.error;

/** 로그인이 안 된(세션 없음/만료) 요청. → 401 UNAUTHENTICATED */
public class UnauthenticatedException extends RuntimeException {
}
