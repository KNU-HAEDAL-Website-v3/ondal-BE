package kr.haedal.ondal.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 가짜 OIDC 제공자(Keycloak 대역) - JDK 내장 HttpServer 로 Discovery·JWKS·token 3개 엔드포인트만 흉내낸다.
 * 로그인 화면은 없다: 테스트가 /api/auth/login 의 302 Location 에서 state·nonce·code_challenge 를 읽고 곧바로 콜백을 부른다.
 * ID 토큰은 여기서 만든 RSA 키로 서명하고 JWKS 엔드포인트가 그 공개키를 내려준다 - BE 의 서명 검증(NimbusJwtDecoder)을 실제로 태운다.
 * token 엔드포인트는 Basic 인증(client_id:secret)·code·PKCE(code_verifier 의 S256 = 기대한 code_challenge)를 검사한다.
 * 다음 토큰의 내용(클레임·aud·거부 여부)은 테스트가 매번 지정하며 한 번 쓰면 기본값으로 돌아간다.
 */
public final class FakeOidcProvider implements AutoCloseable {

    public static final String CLIENT_ID = "ondal-test";
    public static final String CLIENT_SECRET = "test-secret";
    private static final String REALM_PATH = "/realms/test";

    private final HttpServer server;
    private final RSAKey key;
    private final String issuer;

    private volatile String expectedCode = "test-code";
    private volatile String expectedCodeChallenge;
    private volatile Map<String, Object> nextClaims = Map.of();
    private volatile String nextAudience = CLIENT_ID;
    private volatile boolean rejectNextExchange;

    /** token 엔드포인트가 받은 폼 파라미터 - 테스트가 redirect_uri·client_id·code_verifier 전송을 확인한다 */
    public final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();

    private FakeOidcProvider(HttpServer server, RSAKey key) {
        this.server = server;
        this.key = key;
        this.issuer = "http://127.0.0.1:" + server.getAddress().getPort() + REALM_PATH;
    }

    public static FakeOidcProvider start() {
        try {
            RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").keyUse(KeyUse.SIGNATURE).generate();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeOidcProvider provider = new FakeOidcProvider(server, key);
            server.createContext(REALM_PATH + "/.well-known/openid-configuration", provider::discovery);
            server.createContext(REALM_PATH + "/protocol/openid-connect/certs", provider::jwks);
            server.createContext(REALM_PATH + "/protocol/openid-connect/token", provider::token);
            server.start();
            return provider;
        } catch (Exception e) {
            throw new IllegalStateException("FakeOidcProvider 시작 실패", e);
        }
    }

    public String issuer() {
        return issuer;
    }

    public String authorizationEndpoint() {
        return issuer + "/protocol/openid-connect/auth";
    }

    public String endSessionEndpoint() {
        return issuer + "/protocol/openid-connect/logout";
    }

    /** 다음 토큰 교환의 기대값 - 이 code 와 이 code_challenge 에 맞는 code_verifier 가 오면 claims 를 담은 ID 토큰을 준다 */
    public void expectExchange(String code, String codeChallenge, Map<String, Object> claims) {
        this.expectedCode = code;
        this.expectedCodeChallenge = codeChallenge;
        this.nextClaims = claims;
    }

    /** 다음 ID 토큰의 aud 를 다른 클라이언트로 - aud 검증 테스트용 */
    public void nextAudience(String audience) {
        this.nextAudience = audience;
    }

    /** 다음 토큰 교환을 400 invalid_grant 로 거부 */
    public void rejectNextExchange() {
        this.rejectNextExchange = true;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- 엔드포인트 ------------------------------------------------------------------------

    private void discovery(HttpExchange exchange) throws IOException {
        String body = "{"
                + "\"issuer\":\"" + issuer + "\","
                + "\"authorization_endpoint\":\"" + authorizationEndpoint() + "\","
                + "\"token_endpoint\":\"" + issuer + "/protocol/openid-connect/token\","
                + "\"jwks_uri\":\"" + issuer + "/protocol/openid-connect/certs\","
                + "\"end_session_endpoint\":\"" + endSessionEndpoint() + "\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"]"
                + "}";
        respond(exchange, 200, body);
    }

    private void jwks(HttpExchange exchange) throws IOException {
        respond(exchange, 200, new JWKSet(key.toPublicJWK()).toString());
    }

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        tokenRequests.add(form);
        boolean reject = rejectNextExchange;
        String audience = nextAudience;
        Map<String, Object> claims = nextClaims;
        rejectNextExchange = false;
        nextAudience = CLIENT_ID;
        nextClaims = Map.of();

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        if (reject
                || !"authorization_code".equals(form.get("grant_type"))
                || !expectedCode.equals(form.get("code"))
                || form.get("code_verifier") == null
                || !s256(form.get("code_verifier")).equals(expectedCodeChallenge)) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        try {
            String idToken = signIdToken(audience, claims);
            respond(exchange, 200, "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\",\"expires_in\":300,"
                    + "\"id_token\":\"" + idToken + "\"}");
        } catch (Exception e) {
            throw new IOException("ID 토큰 서명 실패", e);
        }
    }

    private String signIdToken(String audience, Map<String, Object> claims) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("azp", CLIENT_ID)
                .claim("typ", "ID");
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                builder.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    // ---- 유틸 ------------------------------------------------------------------------------

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            form.put(name, value);
        }
        return form;
    }

    private static String s256(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
