package kr.haedal.ondal.auth.service;

import kr.haedal.ondal.auth.AuthMode;
import kr.haedal.ondal.auth.oidc.DisplayName;
import kr.haedal.ondal.auth.oidc.OidcHttpClient;
import kr.haedal.ondal.auth.oidc.OidcLoginError;
import kr.haedal.ondal.auth.oidc.OidcLoginException;
import kr.haedal.ondal.auth.oidc.OidcProperties;
import kr.haedal.ondal.auth.oidc.OidcProvider;
import kr.haedal.ondal.auth.oidc.OidcProviderMetadata;
import kr.haedal.ondal.auth.oidc.PendingLogin;
import kr.haedal.ondal.auth.oidc.Pkce;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * 홈페이지(Keycloak) 로그인 - OIDC 인가 코드 흐름 + PKCE, BE 가 코드를 교환한다 (docs 결정 7). ondal.auth.mode=oidc 에서만 뜬다.
 *
 * 흐름:
 *  1. beginLogin: state·nonce·code_verifier 생성 → PendingLogin(세션 보관용) + 홈페이지 인증 페이지 주소
 *  2. 사용자가 홈페이지에서 로그인 → Keycloak 이 redirect_uri(/api/auth/callback)로 code·state 를 붙여 돌려보냄
 *  3. completeLogin: state 대조(CSRF) → 코드 교환(client secret + code_verifier) → ID 토큰 검증(서명·iss·aud·exp: OidcProvider, nonce: 여기)
 *     → 클레임의 username 으로 User 찾거나 생성 + 이름 동기화
 * Ondal 은 비밀번호를 받지 않는다(CLAUDE.md 인증 금지선) - 신원은 Keycloak 이 서명한 ID 토큰으로만 확인한다.
 * 토큰은 로그인 순간에만 쓰고, 이후 API 인증은 기존 세션 그대로다 (docs 결정 5 유지 - 인터셉터·@LoginUser 불변).
 * 실패는 전부 OidcLoginException(error 코드) - 컨트롤러가 FE 로그인 화면 ?error=코드 로 보낸다. 고위험 영역(인증) - PM 담당.
 */
@Service
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.OIDC)
public class OidcAuthService {

    /** 로그인 시작 후 이 시간 안에 콜백이 와야 한다 - 홈페이지 로그인 화면에 오래 머문 뒤 돌아오면 다시 시작 */
    static final Duration PENDING_TTL = Duration.ofMinutes(10);
    /** returnTo 는 세션에 실리므로 길이를 제한한다 */
    static final int MAX_RETURN_TO_LENGTH = 512;
    /** profile: preferred_username·name 클레임을 ID 토큰에 싣게 한다 (Keycloak 기본 클라이언트 스코프) */
    private static final String SCOPE = "openid profile";

    private static final Logger log = LoggerFactory.getLogger(OidcAuthService.class);

    private final OidcProperties properties;
    private final OidcProvider provider;
    private final OidcHttpClient httpClient;
    private final UserService userService;

    public OidcAuthService(OidcProperties properties, OidcProvider provider, OidcHttpClient httpClient, UserService userService) {
        this.properties = properties;
        this.provider = provider;
        this.httpClient = httpClient;
        this.userService = userService;
    }

    /** 로그인 시작 - 세션에 보관할 PendingLogin 과 사용자를 보낼 홈페이지 인증 페이지 주소. Discovery 실패 시 OIDC_UNAVAILABLE */
    public LoginStart beginLogin(String returnTo, String app) {
        OidcProviderMetadata metadata = provider.metadata();
        PendingLogin pending = new PendingLogin(
                Pkce.randomToken(), Pkce.randomToken(), Pkce.randomToken(), safeReturnTo(returnTo),
                properties.knownApp(app), Instant.now());
        URI authorizationUri = UriComponentsBuilder.fromUriString(metadata.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", SCOPE)
                .queryParam("state", pending.state())
                .queryParam("nonce", pending.nonce())
                .queryParam("code_challenge", Pkce.codeChallenge(pending.codeVerifier()))
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUri();
        return new LoginStart(pending, authorizationUri);
    }

    /**
     * 콜백 처리. pending 은 컨트롤러가 세션에서 꺼내 지운 값(없으면 null).
     * 검사 순서: 진행 중인 로그인·state(위조·재사용·만료) → 홈페이지가 보낸 error(취소) → 코드 교환 → ID 토큰(서명·iss·aud·exp·nonce) → 계정 동기화
     */
    public LoginResult completeLogin(PendingLogin pending, String code, String state, String error) {
        if (pending == null || pending.isExpired(PENDING_TTL, Instant.now())) {
            throw new OidcLoginException(OidcLoginError.STATE_MISMATCH, "진행 중인 로그인이 없거나 만료됨");
        }
        if (!constantTimeEquals(state, pending.state())) {
            throw new OidcLoginException(OidcLoginError.STATE_MISMATCH, "state 불일치");
        }
        if (error != null && !error.isBlank()) {
            throw new OidcLoginException(OidcLoginError.ACCESS_DENIED, "홈페이지 응답 error=" + error);
        }
        if (code == null || code.isBlank()) {
            throw new OidcLoginException(OidcLoginError.TOKEN_EXCHANGE_FAILED, "code 없음");
        }

        String rawIdToken = httpClient.exchangeCode(provider.metadata().tokenEndpoint(), code, pending.codeVerifier());
        Jwt idToken = provider.decodeIdToken(rawIdToken);
        if (!constantTimeEquals(idToken.getClaimAsString("nonce"), pending.nonce())) {
            throw new OidcLoginException(OidcLoginError.INVALID_ID_TOKEN, "nonce 불일치");
        }
        String loginId = idToken.getClaimAsString(properties.loginIdClaim());
        if (loginId == null || loginId.isBlank()) {
            throw new OidcLoginException(OidcLoginError.INVALID_ID_TOKEN, "ID 토큰에 클레임 없음: " + properties.loginIdClaim());
        }
        // 한글 이름은 성 + 이름 순서로 다시 붙인다 - Keycloak 의 name 은 "이름 성"(given family) 순서라 "철수 김" 으로 온다
        String name = DisplayName.of(idToken.getClaimAsString(properties.nameClaim()),
                idToken.getClaimAsString("given_name"),
                idToken.getClaimAsString("family_name"));

        // 프로필 사진(구글) - Keycloak 이 picture 클레임을 실어 줄 때만. 없으면 null → 기존 값 유지 (docs 결정 14)
        String avatarUrl = idToken.getClaimAsString(properties.pictureClaim());

        User user;
        try {
            user = userService.syncFromIdentity(loginId, name, avatarUrl);
        } catch (InvalidInputException e) {
            throw new OidcLoginException(OidcLoginError.INVALID_ACCOUNT, e.getMessage(), e);
        }
        return new LoginResult(user, rawIdToken, pending.returnTo());
    }

    /** 로그인 성공 후 돌아갈 FE 주소 - 오리진은 app 키로 설정에서 고르고, 경로는 사이트 내부(returnTo)만 허용 */
    public URI frontendUri(String returnTo, String app) {
        return UriComponentsBuilder.fromUriString(properties.frontendBase(app) + safeReturnTo(returnTo))
                .build()
                .encode()
                .toUri();
    }

    /** 로그인 실패 시 FE 로그인 화면 - 실패 이유는 쿼리 error(OidcLoginError 이름), 원래 목적지는 returnTo 로 넘겨 재시도 후 복귀 */
    public URI loginErrorUri(OidcLoginError error, String returnTo, String app) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.frontendBase(app) + "/login")
                .queryParam("error", error.name());
        String safe = safeReturnTo(returnTo);
        if (!"/".equals(safe)) {
            builder.queryParam("returnTo", safe);
        }
        return builder.encode().build().toUri();
    }

    /**
     * 홈페이지(SSO) 로그아웃 주소 - Keycloak end_session_endpoint + id_token_hint(확인 화면 생략) + post_logout_redirect_uri(FE 로그인 화면).
     * 홈페이지가 주소를 제공하지 않거나 Discovery 가 실패하면 null - Ondal 로그아웃은 이미 끝난 뒤라 실패로 만들지 않는다.
     */
    public String logoutUrl(String idToken, String app) {
        try {
            String endSession = provider.metadata().endSessionEndpoint();
            if (endSession == null || endSession.isBlank()) {
                return null;
            }
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endSession)
                    .queryParam("client_id", properties.clientId())
                    .queryParam("post_logout_redirect_uri", properties.frontendBase(app) + "/login");
            if (idToken != null && !idToken.isBlank()) {
                builder.queryParam("id_token_hint", idToken);
            }
            return builder.encode().build().toUriString();
        } catch (OidcLoginException e) {
            log.warn("[auth] 홈페이지 로그아웃 주소를 만들지 못함 {}: {}", e.error(), e.getMessage());
            return null;
        }
    }

    /**
     * 사이트 내부 경로만 허용 - 외부 주소로 튕기는 오픈 리다이렉트 방지. FE LoginPage.safeReturnTo 와 같은 규칙.
     * "/" 로 시작하되 "//"(프로토콜 상대 주소)·"/\" 는 거부, 너무 길면 거부.
     */
    static String safeReturnTo(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_RETURN_TO_LENGTH) {
            return "/";
        }
        if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
            return "/";
        }
        return value;
    }

    /** state·nonce 대조는 길이·내용에 따른 시간 차이가 없게 - 값이 한 번 쓰고 버리는 난수라 실익은 작지만 습관으로 */
    private static boolean constantTimeEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    /** 로그인 시작 결과 - pending 은 세션에, authorizationUri 는 302 Location 으로 */
    public record LoginStart(PendingLogin pending, URI authorizationUri) {
    }

    /** 로그인 성공 결과 - idToken 은 로그아웃(id_token_hint)용으로 세션에, returnTo 는 302 Location 으로 */
    public record LoginResult(User user, String idToken, String returnTo) {
    }
}
