package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문제 태그 API (TagController) - 조회는 누구나, 등록·수정·삭제는 전역 ADMIN.
 * 어휘 관리를 관리자로 좁힌 이유: 운영진이 자유로 만들면 "DP / 다이나믹프로그래밍 / dp" 로 갈라져 분류가 쓸모없어진다.
 */
class TagApiTest extends ApiTestSupport {

    private long createTag(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tags").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Test
    void 목록은_로그인한_누구나_이름순() throws Exception {
        // 한글·영문이 섞인 정렬은 DB 콜레이션에 달려 있어 단언하지 않는다 - 같은 문자군으로 순서만 고정
        createTag("그리디");
        createTag("그래프");
        mockMvc.perform(get("/api/tags").session(login.member("outsider")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("그래프"))
                .andExpect(jsonPath("$[1].name").value("그리디"));
    }

    @Test
    void 미로그인이면_401() throws Exception {
        mockMvc.perform(get("/api/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    void 등록_수정_삭제는_관리자만_운영진도_403() throws Exception {
        createCohort("C언어", "op1");
        long tagId = createTag("DP");

        mockMvc.perform(post("/api/tags").session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "운영진 태그"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/tags/{id}", tagId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "바꾸기"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tags/{id}", tagId).session(login.member("op1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 이름_중복은_409_공백이나_40자_초과는_400() throws Exception {
        createTag("DP");
        mockMvc.perform(post("/api/tags").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "DP"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(post("/api/tags").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", " "))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tags").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "가".repeat(41)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이름을_고치면_붙어_있던_문제에도_그대로_반영된다() throws Exception {
        createCohort("C언어", "op1");
        long tagId = createTag("dp");
        long problemId = createProblem("문제");
        mockMvc.perform(put("/api/problems/{id}", problemId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "문제", "tagIds", List.of(tagId)))))
                .andExpect(status().isOk());

        // 표기 통일 - 연결은 그대로라 문제 쪽 표시가 함께 바뀐다
        mockMvc.perform(put("/api/tags/{id}", tagId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "다이나믹 프로그래밍"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("op1")))
                .andExpect(jsonPath("$.tags[0].name").value("다이나믹 프로그래밍"));
    }

    @Test
    void 쓰는_문제가_있으면_삭제는_409_떼어내면_삭제된다() throws Exception {
        createCohort("C언어", "op1");
        long tagId = createTag("DP");
        long problemId = createProblem("문제");
        mockMvc.perform(put("/api/problems/{id}", problemId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "문제", "tagIds", List.of(tagId)))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/tags/{id}", tagId).session(login.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(put("/api/problems/{id}", problemId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "문제", "tagIds", List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/tags/{id}", tagId).session(login.admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void 없는_태그는_404() throws Exception {
        mockMvc.perform(delete("/api/tags/{id}", 999_999).session(login.admin()))
                .andExpect(status().isNotFound());
    }
}
