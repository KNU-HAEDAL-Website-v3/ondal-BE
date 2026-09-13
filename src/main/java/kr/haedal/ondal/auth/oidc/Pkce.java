package kr.haedal.ondal.auth.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** PKCE(RFC 7636) 와 state·nonce 용 난수 - 32바이트(256비트) SecureRandom 을 base64url(패딩 없음)로 */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    /** code_verifier·state·nonce 공용 - 43자, URL 에 그대로 실을 수 있는 문자만 */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return BASE64URL.encodeToString(bytes);
    }

    /** code_challenge = base64url(SHA-256(code_verifier)), method S256 */
    public static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64URL.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 지원하지 않는 JVM", e);
        }
    }
}
