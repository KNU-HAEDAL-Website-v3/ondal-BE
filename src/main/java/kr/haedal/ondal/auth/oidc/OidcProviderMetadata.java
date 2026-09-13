package kr.haedal.ondal.auth.oidc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OIDC Discovery 문서({issuer}/.well-known/openid-configuration) 중 Ondal 이 쓰는 항목.
 * 주소를 설정에 하나씩 적는 대신 Keycloak 이 알려주는 값을 쓴다 - realm 이나 Keycloak 버전이 바뀌어도 issuer 하나만 맞으면 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OidcProviderMetadata(
        String issuer,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("jwks_uri") String jwksUri,
        /** RP-Initiated Logout 주소 - 선택 항목이라 null 일 수 있다 */
        @JsonProperty("end_session_endpoint") String endSessionEndpoint
) {
}
