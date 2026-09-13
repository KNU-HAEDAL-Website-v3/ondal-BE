package kr.haedal.ondal.auth;

public final class SessionConst {

    /**
     * 세션에는 User 엔티티가 아니라 id만 넣는다.
     * 엔티티를 통째로 넣으면 이후 DB에서 이름/역할이 바뀌어도 세션 속 낡은 사본을 계속 믿게 된다.
     * id만 저장하고 요청마다 DB에서 최신 상태를 읽는다(LoginUserArgumentResolver).
     */
    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    /** oidc 모드: 로그인 시작(GET /api/auth/login)~콜백 사이에만 존재하는 진행 정보(PendingLogin). 콜백이 읽는 즉시 지운다(1회용) */
    public static final String OIDC_PENDING_LOGIN = "OIDC_PENDING_LOGIN";

    /**
     * oidc 모드: 로그인에 쓴 ID 토큰(raw JWT). 로그아웃 때 홈페이지(Keycloak) 세션까지 끝내는 id_token_hint 로만 쓴다.
     * API 인증에는 쓰지 않는다 - 인증은 여전히 세션(LOGIN_USER_ID)이다 (docs 결정 5 유지).
     */
    public static final String OIDC_ID_TOKEN = "OIDC_ID_TOKEN";

    private SessionConst() {
    }
}
