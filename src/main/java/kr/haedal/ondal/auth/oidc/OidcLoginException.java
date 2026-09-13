package kr.haedal.ondal.auth.oidc;

/**
 * 홈페이지 로그인 흐름의 모든 실패 - error(FE 에 보일 코드) + 메시지(서버 로그용, 응답에는 싣지 않는다).
 * GlobalExceptionHandler 를 타지 않는다: 로그인 두 엔드포인트는 브라우저 이동이라 JSON 대신 FE 로그인 화면으로 302 해야 하고,
 * 그 변환은 OidcAuthController 가 직접 한다.
 */
public class OidcLoginException extends RuntimeException {

    private final OidcLoginError error;

    public OidcLoginException(OidcLoginError error, String detail) {
        super(detail);
        this.error = error;
    }

    public OidcLoginException(OidcLoginError error, String detail, Throwable cause) {
        super(detail, cause);
        this.error = error;
    }

    public OidcLoginError error() {
        return error;
    }
}
