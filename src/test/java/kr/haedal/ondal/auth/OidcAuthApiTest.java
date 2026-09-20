package kr.haedal.ondal.auth;

import kr.haedal.ondal.support.ApiTestSupport;
import kr.haedal.ondal.support.FakeOidcProvider;
import kr.haedal.ondal.user.entity.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 홈페이지(Keycloak) 로그인 - oidc 모드 API 테스트. FakeOidcProvider 가 Keycloak 대역(Discovery·JWKS·token).
 * ondal.auth.mode=oidc 와 동적 issuer 때문에 다른 테스트와 스프링 컨텍스트가 분리된다(이 클래스만 별도 컨텍스트 + PostgreSQL 컨테이너 1개 더).
 * 스텁 모드의 로그인 API 는 AuthApiTest 가, 세션·권한 공통 흐름은 나머지 슬라이스 테스트가 LoginHelper 로 검증한다.
 */
class OidcAuthApiTest extends ApiTestSupport {

    static final FakeOidcProvider IDP = FakeOidcProvider.start();
    static final String REDIRECT_URI = "http://localhost/api/auth/callback";
    static final String FE = "http://fe.test";
    /** 두 번째 FE(HOJ) - 앱별 복귀 주소 검증용 */
    static final String HOJ_FE = "http://hoj.test";

    @DynamicPropertySource
    static void oidcMode(DynamicPropertyRegistry registry) {
        registry.add("ondal.auth.mode", () -> "oidc");
        registry.add("ondal.auth.oidc.issuer", IDP::issuer);
        registry.add("ondal.auth.oidc.client-id", () -> FakeOidcProvider.CLIENT_ID);
        registry.add("ondal.auth.oidc.client-secret", () -> FakeOidcProvider.CLIENT_SECRET);
        registry.add("ondal.auth.oidc.redirect-uri", () -> REDIRECT_URI);
        registry.add("ondal.auth.oidc.frontend-url", () -> FE);
        registry.add("ondal.auth.oidc.frontend-urls.hoj", () -> HOJ_FE);
    }

    @AfterAll
    static void stopIdp() {
        IDP.close();
    }

    // ---- 로그인 시작 -----------------------------------------------------------------------

    @Test
    void 로그인_시작은_홈페이지_인증_페이지로_302_PKCE_state_nonce_포함() throws Exception {
        Started started = startLogin("/cohorts/1");

        Map<String, String> q = started.params();
        assertThat(q).containsEntry("response_type", "code")
                .containsEntry("client_id", FakeOidcProvider.CLIENT_ID)
                .containsEntry("redirect_uri", REDIRECT_URI)
                .containsEntry("scope", "openid profile")
                .containsEntry("code_challenge_method", "S256");
        assertThat(q.get("state")).hasSizeGreaterThanOrEqualTo(32);
        assertThat(q.get("nonce")).hasSizeGreaterThanOrEqualTo(32).isNotEqualTo(q.get("state"));
        assertThat(q.get("code_challenge")).hasSizeGreaterThanOrEqualTo(32);
        assertThat(started.session()).as("콜백에서 대조할 값을 담은 세션이 생긴다").isNotNull();
        // code_verifier 는 URL 에 나가지 않는다 - 세션에만
        assertThat(started.location()).doesNotContain("code_verifier");
    }

    // ---- 콜백 성공 -------------------------------------------------------------------------

    @Test
    void 콜백_성공하면_새_세션으로_returnTo_에_복귀하고_me_는_토큰의_계정() throws Exception {
        Started started = startLogin("/cohorts/1");
        IDP.expectExchange("code-1", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));

        MvcResult callback = callback(started.session(), "code-1", started.state());
        assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo(FE + "/cohorts/1");

        MockHttpSession loginSession = (MockHttpSession) callback.getRequest().getSession(false);
        assertThat(loginSession).isNotNull();
        assertThat(loginSession.getId()).as("세션 고정 방지 - 로그인 전 세션과 다른 id").isNotEqualTo(started.session().getId());
        assertThat(loginSession.getAttribute(SessionConst.OIDC_PENDING_LOGIN)).as("진행 정보는 새 세션에 남지 않는다").isNull();

        mockMvc.perform(get("/api/auth/me").session(loginSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("hong"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.globalRole").value("MEMBER"))    // 첫 로그인은 MEMBER 로 생성
                .andExpect(jsonPath("$.status").value("PENDING"));      // 그리고 승인 대기 - 운영진이 승인·배정해야 열린다 (docs 결정 10)

        // 승인 전에는 me 말고는 전부 403 USER_PENDING
        mockMvc.perform(get("/api/me/cohorts").session(loginSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_PENDING"));

        // 토큰 교환 요청 내용 - redirect_uri·client_id·PKCE verifier 가 실린다 (verifier 의 S256 대조는 IDP 가 했다)
        Map<String, String> tokenRequest = IDP.tokenRequests.getLast();
        assertThat(tokenRequest).containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "code-1")
                .containsEntry("redirect_uri", REDIRECT_URI)
                .containsEntry("client_id", FakeOidcProvider.CLIENT_ID)
                .containsKey("code_verifier");
    }

    @Test
    void 기존_계정은_같은_id_로_로그인되고_이름은_홈페이지_값으로_갱신_역할은_유지() throws Exception {
        User admin = login.adminUser();   // 부트스트랩으로 ADMIN 이 된 "admin" 계정 - 이름은 "관리자"

        Started started = startLogin(null);
        IDP.expectExchange("code-2", started.codeChallenge(), claims(started.nonce(), "admin", "신학철"));
        MvcResult callback = callback(started.session(), "code-2", started.state());
        assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo(FE + "/");

        mockMvc.perform(get("/api/auth/me").session(sessionOf(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.name").value("신학철"))
                .andExpect(jsonPath("$.globalRole").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));   // 이미 있던 계정의 승인 상태는 로그인이 건드리지 않는다
    }

    @Test
    void 이름_클레임이_없으면_loginId_를_이름으로() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-3", started.codeChallenge(), claims(started.nonce(), "kim", null));
        MvcResult callback = callback(started.session(), "code-3", started.state());

        mockMvc.perform(get("/api/auth/me").session(sessionOf(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("kim"));
    }

    // ---- 여러 FE(Ondal / HOJ) - 시작한 앱으로 돌아온다 -------------------------------------

    @Test
    void app_키를_주면_그_FE_로_복귀한다() throws Exception {
        // 오리진은 서버 설정(frontend-urls)에서만 오고, 클라이언트는 키만 보낸다
        Started started = startLogin("/problems/3", "hoj");
        IDP.expectExchange("code-20", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));
        MvcResult callback = callback(started.session(), "code-20", started.state());
        assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo(HOJ_FE + "/problems/3");
    }

    @Test
    void 모르는_app_키는_기본_FE_로_복귀한다() throws Exception {
        // 오픈 리다이렉트 방지 - 설정에 없는 값은 조용히 기본값으로 눕힌다
        Started started = startLogin("/", "https://evil.example");
        IDP.expectExchange("code-21", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));
        MvcResult callback = callback(started.session(), "code-21", started.state());
        assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo(FE + "/");
    }

    @Test
    void app_키로_시작했으면_로그인_실패도_그_FE_의_로그인_화면으로() throws Exception {
        Started started = startLogin("/problems/3", "hoj");
        MvcResult callback = mockMvc.perform(get("/api/auth/callback")
                        .session(started.session())
                        .param("error", "access_denied")
                        .param("state", started.state()))
                .andExpect(status().isFound())
                .andReturn();
        assertThat(callback.getResponse().getRedirectedUrl()).startsWith(HOJ_FE + "/login?error=");
    }

    @Test
    void 한글_이름은_성_이름_순서로_붙여_저장한다() throws Exception {
        // Keycloak 은 name 을 "given family" 로 조립하므로 한국 이름이 "철수 김" 으로 뒤집혀 온다
        Started started = startLogin(null);
        Map<String, Object> claims = claims(started.nonce(), "chulsoo", "철수 김");
        claims.put("given_name", "철수");
        claims.put("family_name", "김");
        IDP.expectExchange("code-13", started.codeChallenge(), claims);
        MvcResult callback = callback(started.session(), "code-13", started.state());

        mockMvc.perform(get("/api/auth/me").session(sessionOf(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김철수"));
    }

    @Test
    void 영문_이름은_name_클레임_그대로_쓴다() throws Exception {
        Started started = startLogin(null);
        Map<String, Object> claims = claims(started.nonce(), "jdoe", "John Doe");
        claims.put("given_name", "John");
        claims.put("family_name", "Doe");
        IDP.expectExchange("code-14", started.codeChallenge(), claims);
        MvcResult callback = callback(started.session(), "code-14", started.state());

        mockMvc.perform(get("/api/auth/me").session(sessionOf(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    void returnTo_가_외부_주소면_루트로_복귀() throws Exception {
        Started started = startLogin("https://evil.example/phish");
        IDP.expectExchange("code-4", started.codeChallenge(), claims(started.nonce(), "lee", "이몽룡"));
        MvcResult callback = callback(started.session(), "code-4", started.state());
        assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo(FE + "/");
    }

    // ---- 콜백 실패 → FE 로그인 화면 ?error= ------------------------------------------------

    @Test
    void state_가_다르면_STATE_MISMATCH_로_FE_로그인_화면_세션은_로그인_안_됨() throws Exception {
        Started started = startLogin("/cohorts/1");
        IDP.expectExchange("code-5", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));

        MvcResult callback = callback(started.session(), "code-5", "forged-state");
        assertLoginError(callback, "STATE_MISMATCH", "/cohorts/1");
        assertThat(IDP.tokenRequests).as("state 가 틀리면 토큰 교환까지 가지 않는다").noneMatch(r -> "code-5".equals(r.get("code")));
        mockMvc.perform(get("/api/auth/me").session(started.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void 세션_없이_콜백하면_STATE_MISMATCH() throws Exception {
        MvcResult callback = mockMvc.perform(get("/api/auth/callback").param("code", "x").param("state", "y"))
                .andExpect(status().isFound())
                .andReturn();
        assertLoginError(callback, "STATE_MISMATCH", null);
    }

    @Test
    void 같은_콜백을_다시_쓰면_STATE_MISMATCH_진행_정보는_1회용() throws Exception {
        Started started = startLogin("/cohorts/1");
        IDP.expectExchange("code-6", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));
        MvcResult first = callback(started.session(), "code-6", started.state());
        assertThat(first.getResponse().getRedirectedUrl()).isEqualTo(FE + "/cohorts/1");

        MvcResult replay = callback(sessionOf(first), "code-6", started.state());
        assertLoginError(replay, "STATE_MISMATCH", null);
    }

    @Test
    void 홈페이지에서_취소하면_ACCESS_DENIED() throws Exception {
        Started started = startLogin("/cohorts/1");
        MvcResult callback = mockMvc.perform(get("/api/auth/callback").session(started.session())
                        .param("error", "access_denied").param("state", started.state()))
                .andExpect(status().isFound())
                .andReturn();
        assertLoginError(callback, "ACCESS_DENIED", "/cohorts/1");
    }

    @Test
    void 토큰_교환이_거부되면_TOKEN_EXCHANGE_FAILED() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-7", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));
        IDP.rejectNextExchange();
        assertLoginError(callback(started.session(), "code-7", started.state()), "TOKEN_EXCHANGE_FAILED", null);
    }

    @Test
    void nonce_가_다르면_INVALID_ID_TOKEN() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-8", started.codeChallenge(), claims("other-nonce", "hong", "홍길동"));
        assertLoginError(callback(started.session(), "code-8", started.state()), "INVALID_ID_TOKEN", null);
    }

    @Test
    void aud_가_다른_클라이언트면_INVALID_ID_TOKEN() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-9", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));
        IDP.nextAudience("someone-else");
        assertLoginError(callback(started.session(), "code-9", started.state()), "INVALID_ID_TOKEN", null);
    }

    @Test
    void loginId_클레임이_없으면_INVALID_ID_TOKEN() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-10", started.codeChallenge(), Map.of("nonce", started.nonce(), "name", "이름만"));
        assertLoginError(callback(started.session(), "code-10", started.state()), "INVALID_ID_TOKEN", null);
    }

    @Test
    void username_이_50자를_넘으면_INVALID_ACCOUNT() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-11", started.codeChallenge(), claims(started.nonce(), "a".repeat(51), "긴 아이디"));
        assertLoginError(callback(started.session(), "code-11", started.state()), "INVALID_ACCOUNT", null);
    }

    // ---- 모드 분리·로그아웃 ---------------------------------------------------------------------

    @Test
    void 스텁_POST_login_은_oidc_모드에_없다_405() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "admin"))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 로그아웃하면_세션이_끝나고_홈페이지_로그아웃_주소를_돌려준다() throws Exception {
        Started started = startLogin(null);
        IDP.expectExchange("code-12", started.codeChallenge(), claims(started.nonce(), "hong", "홍길동"));
        MockHttpSession loginSession = sessionOf(callback(started.session(), "code-12", started.state()));

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").session(loginSession))
                .andExpect(status().isOk())
                .andReturn();
        String logoutUrl = readJson(logout).get("logoutUrl").asString();   // Jackson 3: asText() 는 폐기
        assertThat(logoutUrl).startsWith(IDP.endSessionEndpoint() + "?");
        Map<String, String> q = queryParams(logoutUrl);
        assertThat(q).containsEntry("client_id", FakeOidcProvider.CLIENT_ID)
                .containsEntry("post_logout_redirect_uri", FE + "/login")
                .containsKey("id_token_hint");
        assertThat(q.get("id_token_hint")).as("로그인에 쓴 ID 토큰(JWT 3부분)").contains(".").hasSizeGreaterThan(50);

        mockMvc.perform(get("/api/auth/me").session(loginSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void 세션_없이_로그아웃해도_조용히_200_logoutUrl_은_null() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoutUrl").value(nullValue()));   // 기존 관용구 (CohortApiTest 참고)
    }

    // ---- 헬퍼 ------------------------------------------------------------------------------

    /** GET /api/auth/login 의 결과 - 세션(PendingLogin 보관)과 302 Location 의 쿼리 */
    record Started(MockHttpSession session, String location, Map<String, String> params) {
        String state() { return params.get("state"); }
        String nonce() { return params.get("nonce"); }
        String codeChallenge() { return params.get("code_challenge"); }
    }

    private Started startLogin(String returnTo) throws Exception {
        return startLogin(returnTo, null);
    }

    private Started startLogin(String returnTo, String app) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/login");
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }
        if (app != null) {
            request.param("app", app);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isFound()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(IDP.authorizationEndpoint() + "?");
        return new Started((MockHttpSession) result.getRequest().getSession(false), location, queryParams(location));
    }

    private MvcResult callback(MockHttpSession session, String code, String state) throws Exception {
        return mockMvc.perform(get("/api/auth/callback").session(session).param("code", code).param("state", state))
                .andExpect(status().isFound())
                .andReturn();
    }

    private static MockHttpSession sessionOf(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    /** 실패 콜백은 FE 로그인 화면으로 - ?error=코드, returnTo 는 있을 때만 */
    private static void assertLoginError(MvcResult callback, String error, String expectedReturnTo) {
        String location = callback.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(FE + "/login?");
        Map<String, String> q = queryParams(location);
        assertThat(q).containsEntry("error", error);
        if (expectedReturnTo == null) {
            assertThat(q).doesNotContainKey("returnTo");
        } else {
            assertThat(q).containsEntry("returnTo", expectedReturnTo);
        }
    }

    /** nonce + username(+ name) - 실제 Keycloak 이 profile 스코프로 넣는 클레임 이름 그대로 */
    // ---- 프로필 사진 (docs 결정 14) ----------------------------------------------------------

    @Test
    void picture_클레임은_avatarUrl_로_저장_없으면_기존_값_유지_https_아니면_무시() throws Exception {
        String photo = "https://lh3.googleusercontent.com/a/photo=s96-c";

        // 1) picture 있음 → 저장
        Started first = startLogin("/cohorts/1");
        Map<String, Object> withPicture = claims(first.nonce(), "pic", "사진왕");
        withPicture.put("picture", photo);
        IDP.expectExchange("code-p1", first.codeChallenge(), withPicture);
        MockHttpSession s1 = (MockHttpSession) callback(first.session(), "code-p1", first.state()).getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(s1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(photo));

        // 2) 다음 로그인에 picture 없음(매퍼가 빠진 경우) → 기존 값 유지
        Started second = startLogin("/cohorts/1");
        IDP.expectExchange("code-p2", second.codeChallenge(), claims(second.nonce(), "pic", "사진왕"));
        MockHttpSession s2 = (MockHttpSession) callback(second.session(), "code-p2", second.state()).getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(s2))
                .andExpect(jsonPath("$.avatarUrl").value(photo));

        // 3) https 가 아닌 값은 무시 - 화면이 <img src> 로 바로 쓴다
        Started third = startLogin("/cohorts/1");
        Map<String, Object> bad = claims(third.nonce(), "pic", "사진왕");
        bad.put("picture", "javascript:alert(1)");
        IDP.expectExchange("code-p3", third.codeChallenge(), bad);
        MockHttpSession s3 = (MockHttpSession) callback(third.session(), "code-p3", third.state()).getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(s3))
                .andExpect(jsonPath("$.avatarUrl").value(photo));

        // 4) 사진이 없는 계정은 null (화면은 이름 첫 글자)
        Started fourth = startLogin("/cohorts/1");
        IDP.expectExchange("code-p4", fourth.codeChallenge(), claims(fourth.nonce(), "nopic", "무사진"));
        MockHttpSession s4 = (MockHttpSession) callback(fourth.session(), "code-p4", fourth.state()).getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/me").session(s4))
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));
    }

    private static Map<String, Object> claims(String nonce, String loginId, String name) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("nonce", nonce);
        claims.put("preferred_username", loginId);
        if (name != null) {
            claims.put("name", name);
        }
        return claims;
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(name, value);
        }
        return params;
    }
}
