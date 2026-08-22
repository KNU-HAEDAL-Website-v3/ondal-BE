package kr.haedal.hoj.auth;

import kr.haedal.hoj.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인 API 자체의 동작 - 다른 테스트는 LoginHelper 로 세션을 직접 만들므로 여기서만 실제 로그인을 탄다 */
class AuthApiTest extends ApiTestSupport {

    @Test
    void 로그인하면_세션이_생기고_me가_200_로그아웃하면_다시_401() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginId", "newcomer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("newcomer"))
                .andExpect(jsonPath("$.globalRole").value("MEMBER"))   // 첫 로그인은 MEMBER 로 생성
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("newcomer"));

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 세션은_있지만_사용자가_삭제됐으면_401() throws Exception {
        var ghost = login.memberUser("ghost");
        MockHttpSession session = login.as(ghost);
        userRepository.delete(ghost);
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 세션_없이_me는_401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 세션_없이_로그아웃해도_조용히_200() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void health는_로그인_없이_200() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
