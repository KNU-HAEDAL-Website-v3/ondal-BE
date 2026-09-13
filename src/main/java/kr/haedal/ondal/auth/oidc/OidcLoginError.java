package kr.haedal.ondal.auth.oidc;

/**
 * 홈페이지 로그인 실패 코드 - FE 로그인 화면으로 302 할 때 쿼리 error 에 실린다 (예: /login?error=STATE_MISMATCH).
 * FE 는 이 이름으로 안내 문구를 고른다. 어느 경우든 다시 "홈페이지 계정으로 로그인" 을 누르면 처음부터 새로 시작된다.
 */
public enum OidcLoginError {
    /** 사용자가 홈페이지 로그인 화면에서 취소·거부 - Keycloak 이 error 파라미터로 돌려보냄 */
    ACCESS_DENIED,
    /** 진행 중인 로그인이 없거나(쿠키 없음·10분 경과·같은 콜백 재사용) state 가 다름 - 위조된 콜백 가능성 */
    STATE_MISMATCH,
    /** 인가 코드 → 토큰 교환 실패 - 코드 재사용·만료, client secret 또는 redirect_uri 불일치 (서버 로그에 Keycloak 응답 본문) */
    TOKEN_EXCHANGE_FAILED,
    /** ID 토큰 서명·iss·aud·exp·nonce 검증 실패, 또는 loginId 클레임 없음 */
    INVALID_ID_TOKEN,
    /** 홈페이지 계정 값이 Ondal 규칙에 안 맞음 - username 50자 초과 등 */
    INVALID_ACCOUNT,
    /** Discovery 문서를 못 가져옴 - Keycloak 다운, nginx keycloak.conf 미적용, issuer 설정 오류 */
    OIDC_UNAVAILABLE
}
