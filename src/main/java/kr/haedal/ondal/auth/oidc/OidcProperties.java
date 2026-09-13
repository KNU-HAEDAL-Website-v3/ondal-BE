package kr.haedal.ondal.auth.oidc;

import jakarta.validation.constraints.NotBlank;
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
        /** 로그인 후 돌아갈 FE 주소(끝에 / 없이). 실패는 {frontendUrl}/login?error=코드 */
        @NotBlank String frontendUrl,
        /** loginId 로 쓸 ID 토큰 클레임 - Keycloak username. 홈페이지 정책상 다른 클레임을 써야 하면 여기만 바꾼다 */
        @DefaultValue("preferred_username") String loginIdClaim,
        /** 표시 이름으로 쓸 클레임. 없으면 loginId 로 채운다 */
        @DefaultValue("name") String nameClaim
) {

    /** 끝 슬래시를 뗀 issuer - Discovery 주소 조립과 iss 비교에 같은 형태를 쓴다 */
    public String normalizedIssuer() {
        return stripTrailingSlash(issuer);
    }

    /** 끝 슬래시를 뗀 FE 주소 - 뒤에 "/login"·returnTo 를 붙일 때 "//" 가 되지 않게 */
    public String frontendBase() {
        return stripTrailingSlash(frontendUrl);
    }

    static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
