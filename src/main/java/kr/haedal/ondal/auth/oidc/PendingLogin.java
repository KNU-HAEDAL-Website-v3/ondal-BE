package kr.haedal.ondal.auth.oidc;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * 로그인 시작(GET /api/auth/login)에서 만들어 세션에 두고, 콜백에서 꺼내 대조하는 값들. 한 번 쓰면 지운다(OidcAuthController).
 *  - state: 콜백이 우리가 시작한 로그인의 응답인지 (CSRF·로그인 CSRF 방어)
 *  - nonce: 받은 ID 토큰이 이 로그인 시도에 발급된 것인지 (토큰 재생 방어)
 *  - codeVerifier: PKCE - 인가 코드를 가로챈 쪽이 토큰으로 바꾸지 못하게 (URL 에는 SHA-256 한 code_challenge 만 나간다)
 *  - returnTo: 로그인 후 돌아갈 FE 내부 경로 (이미 검증된 값)
 *  - app: 로그인을 시작한 FE 앱 키 (예: hoj). 설정에 등록된 키만 들어온다 - 콜백에서 그 앱으로 돌려보내려고.
 *         null 이면 기본 FE(Ondal)
 */
public record PendingLogin(String state, String nonce, String codeVerifier, String returnTo, String app, Instant createdAt)
        implements Serializable {

    public boolean isExpired(Duration ttl, Instant now) {
        return createdAt.plus(ttl).isBefore(now);
    }
}
