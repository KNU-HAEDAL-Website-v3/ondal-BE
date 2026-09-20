package kr.haedal.ondal.problem;

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

/**
 * 문제 목록·상세의 P3 확장 필드 (docs hoj/api.md 1절) - 푼 사람 수·채점된 제출 수·정답률·내 상태.
 * 엔진 = FakeJudgeEngine(echo): 기대 출력 = 입력이면 ACCEPTED, `judge: WA` 면 WRONG_ANSWER.
 */
class ProblemStatsApiTest extends ApiTestSupport {

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

    private void submit(long problemId, String loginId, String code) throws Exception {
        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", code, "language", "C"))))
                .andExpect(status().isCreated());
    }

    @Test
    void 푼_사람_수_채점된_제출_수_정답률_내_상태() throws Exception {
        long a = createProblem("A+B");
        enableEchoJudge(a);
        createProblem("채점 없는 문제");

        mockMvc.perform(get("/api/problems").session(login.member("s1")))
                .andExpect(jsonPath("$[0].solvedUserCount").value(0))
                .andExpect(jsonPath("$[0].submissionCount").value(0))
                .andExpect(jsonPath("$[0].acceptedRate").doesNotExist())
                .andExpect(jsonPath("$[0].myStatus").value("NONE"))
                .andExpect(jsonPath("$[0].solved").value(false));

        submit(a, "s1", "int main(){} // judge: WA");
        mockMvc.perform(get("/api/problems").session(login.member("s1")))
                .andExpect(jsonPath("$[0].myStatus").value("ATTEMPTED"))
                .andExpect(jsonPath("$[0].solved").value(false))
                .andExpect(jsonPath("$[0].submissionCount").value(1))
                .andExpect(jsonPath("$[0].acceptedRate").value(0))
                .andExpect(jsonPath("$[0].solvedUserCount").value(0));

        submit(a, "s1", "int main(){}");
        submit(a, "s2", "int main(){}");
        mockMvc.perform(get("/api/problems").session(login.member("s1")))
                .andExpect(jsonPath("$[0].myStatus").value("SOLVED"))
                .andExpect(jsonPath("$[0].solved").value(true))
                .andExpect(jsonPath("$[0].submissionCount").value(3))
                .andExpect(jsonPath("$[0].acceptedRate").value(66))     // 2/3 = 66.6 → 소수점 버림
                .andExpect(jsonPath("$[0].solvedUserCount").value(2))
                // 채점 없는 문제는 전부 0·null·NONE
                .andExpect(jsonPath("$[1].submissionCount").value(0))
                .andExpect(jsonPath("$[1].acceptedRate").doesNotExist())
                .andExpect(jsonPath("$[1].myStatus").value("NONE"));

        // 상세도 같은 값 - 내 상태만 사람마다 다르다
        mockMvc.perform(get("/api/problems/{id}", a).session(login.member("s2")))
                .andExpect(jsonPath("$.myStatus").value("SOLVED"))
                .andExpect(jsonPath("$.solvedUserCount").value(2))
                .andExpect(jsonPath("$.submissionCount").value(3))
                .andExpect(jsonPath("$.acceptedRate").value(66));
        mockMvc.perform(get("/api/problems/{id}", a).session(login.member("s3")))
                .andExpect(jsonPath("$.myStatus").value("NONE"))
                .andExpect(jsonPath("$.solved").value(false))
                .andExpect(jsonPath("$.solvedUserCount").value(2));
    }
}
