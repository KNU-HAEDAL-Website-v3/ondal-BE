package kr.haedal.ondal.user;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 마이페이지 활동 요약 (MeController GET /api/me/stats) */
class MeApiTest extends ApiTestSupport {

    @Test
    void 로그인_없이는_401() throws Exception {
        mockMvc.perform(get("/api/me/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void 활동이_없으면_전부_0_가입_시각은_있다() throws Exception {
        mockMvc.perform(get("/api/me/stats").session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinedAt").isNotEmpty())
                .andExpect(jsonPath("$.assignmentSubmissions").value(0))
                .andExpect(jsonPath("$.practiceSubmissions").value(0))
                .andExpect(jsonPath("$.solvedProblems").value(0));
    }

    @Test
    void 연습_제출과_맞힌_문제가_본인_것만_집계된다() throws Exception {
        long echo = createProblem("에코");
        // 가짜 엔진은 입력을 그대로 출력한다 - 기대 출력 = 입력이면 ACCEPTED (FakeJudgeEngine)
        Map<String, Object> config = new HashMap<>();
        config.put("testCases", List.of(Map.of("input", "hi\n", "expectedOutput", "hi\n", "isPublic", true)));
        config.put("rejudge", false);
        mockMvc.perform(put("/api/problems/{id}/judge", echo).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {   // 재제출도 건수에 든다, 맞힌 문제는 1개
            mockMvc.perform(post("/api/problems/{id}/submissions", echo).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("codeText", "print(input())", "language", "Python 3"))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/problems/{id}/submissions", echo).session(login.member("s2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", "print(input())", "language", "Python 3"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/stats").session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentSubmissions").value(0))
                .andExpect(jsonPath("$.practiceSubmissions").value(2))
                .andExpect(jsonPath("$.solvedProblems").value(1));
        mockMvc.perform(get("/api/me/stats").session(login.member("nobody")))
                .andExpect(jsonPath("$.practiceSubmissions").value(0))
                .andExpect(jsonPath("$.solvedProblems").value(0));
    }
}
