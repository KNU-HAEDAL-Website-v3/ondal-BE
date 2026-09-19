package kr.haedal.ondal.user;

import kr.haedal.ondal.support.ApiTestSupport;
import kr.haedal.ondal.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자(MAINTAINER) 전역 역할 - 해구르르(ADMIN)와 권한이 같고 표시 명칭만 "관리자" (docs 결정 12).
 * 권한 판정은 User.isAdmin() 하나를 지나므로, 관리자 전용·운영진 이상 API 를 대표로 하나씩 확인한다.
 */
@DisplayName("관리자(MAINTAINER) 전역 역할")
class MaintainerRoleApiTest extends ApiTestSupport {

    @Test
    void me_응답은_MAINTAINER_이고_바로_이용_가능하다() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(login.maintainer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("maintainer"))
                .andExpect(jsonPath("$.globalRole").value("MAINTAINER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 관리자_전용_API_를_해구르르와_똑같이_쓴다() throws Exception {
        // 분반 생성 (@AdminOnly)
        mockMvc.perform(post("/api/cohorts").session(login.maintainer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "관리자가 만든 반", "description", "설명", "operatorLoginIds", List.of()))))
                .andExpect(status().isCreated());

        // 태그 등록 (@AdminOnly - 어휘 관리)
        mockMvc.perform(post("/api/tags").session(login.maintainer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "구현"))))
                .andExpect(status().isCreated());

        // 문제 번들 가져오기 (@AdminOnly)
        Map<String, Object> bundle = Map.of("problems", List.of(
                Map.of("problemNo", 2001, "title", "두 수의 합", "tags", List.of("구현", "수학"), "testCases", List.of())));
        mockMvc.perform(post("/api/problems/import").session(login.maintainer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(bundle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    @Test
    void 운영진_이상_API_도_통과한다_부원_목록과_승인() throws Exception {
        User newbie = userRepository.save(User.pending("newbie", "신입"));

        mockMvc.perform(get("/api/users").session(login.maintainer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loginId").value("newbie"));   // 승인 대기가 맨 앞

        mockMvc.perform(post("/api/users/" + newbie.getId() + "/approve").session(login.maintainer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 분반_화면의_직책은_관리자로_해구르르는_그대로_해구르르다() throws Exception {
        long id = createCohort("C언어", "op1");

        mockMvc.perform(get("/api/cohorts/{id}", id).session(login.maintainer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value(nullValue()))
                .andExpect(jsonPath("$.myTitle").value("관리자"))
                .andExpect(jsonPath("$.canManage").value(true));

        mockMvc.perform(get("/api/cohorts/{id}", id).session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myTitle").value("해구르르"));
    }

    @Test
    void 일반_부원은_여전히_관리자_전용_API_에_403() throws Exception {
        mockMvc.perform(post("/api/tags").session(login.member("plain"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "구현"))))
                .andExpect(status().isForbidden());
    }
}
