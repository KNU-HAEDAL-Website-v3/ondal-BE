package kr.haedal.ondal.auth.oidc;

import kr.haedal.ondal.auth.AuthMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.util.Collection;

/**
 * 홈페이지(Keycloak) 의 Discovery 결과와 ID 토큰 검증기를 첫 사용 시 만들어 보관한다.
 *
 * 기동 시점에 Keycloak 을 부르지 않는 이유: IdP 가 잠시 죽어 있어도 Ondal 은 떠야 한다(이미 로그인된 세션은 멀쩡히 동작).
 * 실패하면 보관하지 않고 다음 로그인 시도에서 다시 가져온다. 성공한 뒤에는 재조회하지 않는다 - realm 설정을 바꾸면 재배포.
 * 검증기(NimbusJwtDecoder)는 JWKS 를 내부 캐시하고 모르는 kid 가 오면 다시 받는다 - Keycloak 키 회전에 자동 대응.
 */
@Component
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.OIDC)
public class OidcProvider {

    private final OidcProperties properties;
    private final OidcHttpClient httpClient;

    private volatile OidcProviderMetadata metadata;
    private volatile JwtDecoder idTokenDecoder;

    public OidcProvider(OidcProperties properties, OidcHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /** Discovery 결과 (첫 호출 때 조회). 실패는 OidcLoginException(OIDC_UNAVAILABLE) */
    public OidcProviderMetadata metadata() {
        OidcProviderMetadata current = metadata;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (metadata == null) {
                OidcProviderMetadata fetched = httpClient.fetchMetadata();
                idTokenDecoder = buildIdTokenDecoder(fetched); // metadata 보다 먼저 - metadata 가 보이면 디코더도 반드시 있다
                metadata = fetched;
            }
            return metadata;
        }
    }

    /**
     * ID 토큰 검증 - 서명(JWKS, RS256)·iss·exp/nbf(60초 여유)·aud(우리 client_id 포함)·azp(있으면 우리 client_id).
     * nonce 는 로그인 시도마다 다른 값이라 여기가 아니라 OidcAuthService 가 PendingLogin 과 대조한다.
     */
    public Jwt decodeIdToken(String rawIdToken) {
        metadata();
        try {
            return idTokenDecoder.decode(rawIdToken);
        } catch (JwtException e) {
            throw new OidcLoginException(OidcLoginError.INVALID_ID_TOKEN, "ID 토큰 검증 실패: " + e.getMessage(), e);
        }
    }

    private JwtDecoder buildIdTokenDecoder(OidcProviderMetadata metadata) {
        // JWKS 요청에도 타임아웃을 건다 - 기본 RestTemplate 은 무한 대기
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(OidcHttpClient.TIMEOUT).build());
        requestFactory.setReadTimeout(OidcHttpClient.TIMEOUT);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(metadata.jwksUri())
                .restOperations(new RestTemplate(requestFactory))
                .build();

        String clientId = properties.clientId();
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId));
        // aud 가 여럿이면 azp 가 반드시 우리여야 한다(OIDC Core 3.1.3.7) - Keycloak 은 항상 azp 를 넣는다
        OAuth2TokenValidator<Jwt> authorizedParty = new JwtClaimValidator<String>(
                "azp", azp -> azp == null || azp.equals(clientId));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.normalizedIssuer()), audience, authorizedParty));
        return decoder;
    }
}
