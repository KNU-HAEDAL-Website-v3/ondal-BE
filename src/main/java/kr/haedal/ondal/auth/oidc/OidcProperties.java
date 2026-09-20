package kr.haedal.ondal.auth.oidc;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 홈페이지(Keycloak) OIDC 연동 설정 - application.yml prod 절의 ondal.auth.oidc.* (값은 .env 의 OIDC_*·FE_URL 에서).
 * 필수값이 비면 기동 실패(fail-fast) - 첫 로그인 때가 아니라 배포 순간에 드러나게. OidcConfig 가 oidc 모드에서만 바인딩한다.
 */
@ConfigurationProperties(prefix = "ondal.auth.oidc")
@Validated
public record OidcProperties(
        /** realm 의 issuer - Discovery 주소({issuer}/.well-known/openid-configuration)의 밑동이자 ID 토큰 iss 와 비교할 값 */
        @NotBlank String issuer,
        /** Keycloak 클라이언트 ID (confidential) */
        @NotBlank String clientId,
        /** Keycloak 클라이언트 secret - 토큰 교환 때 client_secret_basic 으로 보낸다 */
        @NotBlank String clientSecret,
        /** 콜백 주소 - Keycloak 에 등록된 Valid redirect URIs 와 문자 단위로 같아야 한다 */
        @NotBlank String redirectUri,
        /** 로그인 후 돌아갈 기본 FE 주소(끝에 / 없이). 실패는 {frontendUrl}/login?error=코드 */
        @NotBlank String frontendUrl,
        /**
         * FE 가 여럿일 때 app 키 -> 그 앱의 주소 (예: hoj -> https://oj.haedal-...).
         * 오리진은 **설정에서만** 온다 - 클라이언트는 키만 보내므로 오픈 리다이렉트가 되지 않는다.
         * 비어 있으면 모든 요청이 frontendUrl 로 돌아간다(단일 FE 시절과 동일).
         */
        @DefaultValue Map<String, String> frontendUrls,
        /** loginId 로 쓸 ID 토큰 클레임 - Keycloak username. 홈페이지 정책상 다른 클레임을 써야 하면 여기만 바꾼다 */
        @DefaultValue("preferred_username") String loginIdClaim,
        /** 표시 이름으로 쓸 클레임. 없으면 loginId 로 채운다 */
        @DefaultValue("name") String nameClaim,
        /** 프로필 사진 주소 클레임(구글 IdP 의 picture). Keycloak 에 매퍼가 없으면 클레임이 없고, 그때는 사진 없이 동작한다 */
        @DefaultValue("picture") String pictureClaim
) {

    /** 끝 슬래시를 뗀 issuer - Discovery 주소 조립과 iss 비교에 같은 형태를 쓴다 */
    public String normalizedIssuer() {
        return stripTrailingSlash(issuer);
    }

    /** 끝 슬래시를 뗀 기본 FE 주소 - 뒤에 "/login"·returnTo 를 붙일 때 "//" 가 되지 않게 */
    public String frontendBase() {
        return stripTrailingSlash(frontendUrl);
    }

    /** app 키에 등록된 FE 주소. 키가 null·미등록이면 기본 주소 - 모르는 값이 와도 안전하게 Ondal 로 */
    public String frontendBase(String app) {
        String mapped = app == null || frontendUrls == null ? null : frontendUrls.get(app);
        return mapped == null ? frontendBase() : stripTrailingSlash(mapped);
    }

    /** 설정에 등록된 app 키인가 - 아니면 null 로 눕혀 기본 FE 로 보낸다 */
    public String knownApp(String app) {
        return app != null && frontendUrls != null && frontendUrls.containsKey(app) ? app : null;
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
