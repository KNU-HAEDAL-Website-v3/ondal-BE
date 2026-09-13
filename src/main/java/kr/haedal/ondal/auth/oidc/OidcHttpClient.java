package kr.haedal.ondal.auth.oidc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import kr.haedal.ondal.auth.AuthMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Keycloak 에 보내는 HTTP 요청 2가지 - Discovery 문서 조회, 인가 코드 → 토큰 교환. (JWKS 는 OidcProvider 의 디코더가 직접 받는다)
 * 응답은 문자열로 받아 Boot 의 ObjectMapper 로 읽는다 - 오류 응답(400 JSON)도 같은 경로로 진단할 수 있게.
 * 실패는 전부 OidcLoginException 으로 바꾼다 - 로그인 흐름의 호출자는 HTTP 예외 종류를 알 필요가 없다.
 */
@Component
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.OIDC)
public class OidcHttpClient {

    /** Keycloak 은 같은 서버(Cloudflare 경유)에 있다 - 이보다 오래 걸리면 장애로 보고 로그인을 실패시키는 편이 낫다 */
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";

    private final OidcProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OidcHttpClient(OidcProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Discovery 문서 조회. 문서의 issuer 는 설정한 issuer 와 정확히 같아야 한다(스펙) - 다른 realm 을 가리키는 설정 실수를 여기서 잡는다 */
    public OidcProviderMetadata fetchMetadata() {
        String issuer = properties.normalizedIssuer();
        String url = issuer + DISCOVERY_PATH;
        OidcProviderMetadata metadata;
        try {
            String body = restClient.get().uri(url).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
            metadata = objectMapper.readValue(body, OidcProviderMetadata.class);
        } catch (RuntimeException e) {
            throw new OidcLoginException(OidcLoginError.OIDC_UNAVAILABLE, "Discovery 문서를 가져오지 못함: " + url, e);
        }
        if (metadata.issuer() == null || !issuer.equals(OidcProperties.stripTrailingSlash(metadata.issuer()))) {
            throw new OidcLoginException(OidcLoginError.OIDC_UNAVAILABLE,
                    "Discovery 문서의 issuer 가 설정과 다름: " + metadata.issuer() + " (설정: " + issuer + ")");
        }
        if (isBlank(metadata.authorizationEndpoint()) || isBlank(metadata.tokenEndpoint()) || isBlank(metadata.jwksUri())) {
            throw new OidcLoginException(OidcLoginError.OIDC_UNAVAILABLE, "Discovery 문서에 필수 엔드포인트가 없음: " + url);
        }
        return metadata;
    }

    /**
     * 인가 코드 → 토큰 교환 (grant_type=authorization_code). 클라이언트 인증은 client_secret_basic(Authorization: Basic),
     * PKCE 의 code_verifier 를 함께 보낸다. 돌려주는 값은 ID 토큰(raw JWT) - 검증은 OidcProvider.decodeIdToken.
     */
    public String exchangeCode(String tokenEndpoint, String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        form.add("client_id", properties.clientId());
        form.add("code_verifier", codeVerifier);

        TokenResponse token;
        try {
            String body = restClient.post().uri(tokenEndpoint)
                    .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret(), StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            token = objectMapper.readValue(body, TokenResponse.class);
        } catch (RuntimeException e) {
            // 4xx 본문(invalid_grant 등)은 예외 메시지에 실려 로그로 간다 - 코드 재사용·redirect_uri 불일치·secret 오류의 진단 근거
            throw new OidcLoginException(OidcLoginError.TOKEN_EXCHANGE_FAILED, "토큰 교환 실패: " + e.getMessage(), e);
        }
        if (isBlank(token.idToken())) {
            throw new OidcLoginException(OidcLoginError.TOKEN_EXCHANGE_FAILED, "토큰 응답에 id_token 이 없음 - scope 에 openid 가 있는지 확인");
        }
        return token.idToken();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 토큰 응답 중 쓰는 항목 - access_token 은 홈페이지 API 를 부르지 않으므로 버린다 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("id_token") String idToken) {
    }
}
