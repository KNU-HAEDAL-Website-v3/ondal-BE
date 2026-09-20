package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 북마크 API (ProblemBookmarkController) - 멱등 PUT/DELETE, 읽기는 목록·상세의 bookmarked (V11, docs hoj/api.md 7절) */
class ProblemBookmarkApiTest extends ApiTestSupport {

    @Test
    void 북마크는_멱등이고_목록_상세의_bookmarked_로_읽는다() throws Exception {
        long problemId = createProblem("A+B");
        createProblem("다른 문제");

        mockMvc.perform(get("/api/problems").session(login.member("s1")))
                .andExpect(jsonPath("$[0].bookmarked").value(false));

        mockMvc.perform(put("/api/problems/{id}/bookmark", problemId).session(login.member("s1")))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/problems/{id}/bookmark", problemId).session(login.member("s1")))
                .andExpect(status().isNoContent());   // 두 번 눌러도 그대로

        mockMvc.perform(get("/api/problems").session(login.member("s1")))
                .andExpect(jsonPath("$[0].bookmarked").value(true))
                .andExpect(jsonPath("$[1].bookmarked").value(false));
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(jsonPath("$.bookmarked").value(true));
        // 남의 북마크는 내 화면에 보이지 않는다
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s2")))
                .andExpect(jsonPath("$.bookmarked").value(false));

        mockMvc.perform(delete("/api/problems/{id}/bookmark", problemId).session(login.member("s1")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/problems/{id}/bookmark", problemId).session(login.member("s1")))
                .andExpect(status().isNoContent());   // 없어도 204
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(jsonPath("$.bookmarked").value(false));
    }

    @Test
    void 없는_문제는_404_미로그인은_401() throws Exception {
        mockMvc.perform(put("/api/problems/{id}/bookmark", 999_999).session(login.member("s1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/problems/{id}/bookmark", 999_999).session(login.member("s1")))
                .andExpect(status().isNotFound());
        long problemId = createProblem("A+B");
        mockMvc.perform(put("/api/problems/{id}/bookmark", problemId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 북마크된_문제도_삭제된다_북마크가_함께_지워진다() throws Exception {
        long problemId = createProblem("지울 문제");
        mockMvc.perform(put("/api/problems/{id}/bookmark", problemId).session(login.member("s1")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/problems/{id}", problemId).session(login.admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(status().isNotFound());
    }
}
