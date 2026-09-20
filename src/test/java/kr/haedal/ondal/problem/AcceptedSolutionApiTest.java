package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다른 사람 풀이 API (AcceptedSolutionController) - 맞힌 사람·운영진만, 연습 제출의 ACCEPTED 를 사용자당 최신 1건 (docs hoj/api.md 5절).
 * 엔진 = FakeJudgeEngine(echo): 기대 출력 = 입력이면 ACCEPTED, 코드에 `judge: WA` 가 있으면 WRONG_ANSWER.
 */
class AcceptedSolutionApiTest extends ApiTestSupport {

    /** 기대 출력 = 입력인 케이스 하나 - echo 엔진 기준 정답 */
    private void enableEchoJudge(long problemId) throws Exception {
        Map<String, Object> tc = new HashMap<>();
        tc.put("input", "1 2\n");
        tc.put("expectedOutput", "1 2\n");
        tc.put("isPublic", true);
        Map<String, Object> body = new HashMap<>();
        body.put("testCases", List.of(tc));
        body.put("rejudge", false);
        mockMvc.perform(put("/api/problems/{id}/judge", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private void submit(long problemId, String loginId, String code, String language) throws Exception {
        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", code, "language", language))))
                .andExpect(status().isCreated());
    }

    @Test
    void 못_푼_사람은_403_NOT_SOLVED_맞히면_남의_최신_정답을_사용자당_하나씩_본인_제외() throws Exception {
        long problemId = createProblem("A+B");
        enableEchoJudge(problemId);
        submit(problemId, "s2", "int main(){} // s2 first", "C");
        submit(problemId, "s2", "int main(){} // s2 second", "C");          // 같은 사람은 최신 1건만
        submit(problemId, "s3", "print(1) # judge: WA", "Python 3");         // 오답은 빠진다
        submit(problemId, "s4", "print(1) # s4", "Python 3");

        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId).session(login.member("s1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_SOLVED"));

        submit(problemId, "s1", "int main(){} // s1", "C");
        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId).session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].user.name").value("s4"))                     // 최신 먼저
                .andExpect(jsonPath("$[0].user.loginId").doesNotExist())
                .andExpect(jsonPath("$[0].language").value("Python 3"))
                .andExpect(jsonPath("$[0].maxTimeMs").exists())
                .andExpect(jsonPath("$[1].user.name").value("s2"))
                .andExpect(jsonPath("$[1].codeText").value("int main(){} // s2 second"))
                .andExpect(jsonPath("$[1].submissionId").exists());

        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId).param("language", "C").session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user.name").value("s2"));

        // 본인 것은 빠진다 - s2 가 보면 s1·s4
        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId).session(login.member("s2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].user.name").value("s1"))
                .andExpect(jsonPath("$[1].user.name").value("s4"));
    }

    @Test
    void 운영진은_풀지_않아도_본다_없는_문제는_404() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");
        enableEchoJudge(problemId);
        submit(problemId, "s1", "int main(){}", "C");

        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId).session(login.member("op1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId).session(login.admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", 999_999).session(login.admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/problems/{id}/accepted-solutions", problemId))
                .andExpect(status().isUnauthorized());
    }
}
