package kr.haedal.ondal.auth;

/**
 * 인증 모드 - 설정 키 ondal.auth.mode 의 값. 빈 등록 조건(@ConditionalOnProperty)에 쓰는 상수.
 *
 *  - stub: loginId 만 보내면 통과 (local·test). 기본값(키가 없으면 stub)
 *  - oidc: 홈페이지 로그인 (Keycloak, OIDC 인가 코드 + PKCE, BE 가 코드 교환). prod 프로필은 반드시 이 값 -
 *          prod 에서 stub 이면 StubAuthService 가 기동을 막는다
 *
 * 모드별로 한 쪽만 뜬다 (같은 경로 /api/auth/login 을 stub 은 POST, oidc 는 GET 으로 쓴다):
 *  - stub: StubAuthController + StubAuthService
 *  - oidc: OidcAuthController + OidcAuthService + auth/oidc 패키지
 * 모드와 무관한 공통: AuthController(me·logout), LoginSession, AuthInterceptor, AuthorizationInterceptor, @LoginUser, 세션 상수
 */
public final class AuthMode {

    public static final String PROPERTY = "ondal.auth.mode";
    public static final String STUB = "stub";
    public static final String OIDC = "oidc";

    private AuthMode() {
    }
}
