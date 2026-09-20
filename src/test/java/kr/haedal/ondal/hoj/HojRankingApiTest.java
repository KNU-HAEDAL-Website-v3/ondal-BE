package kr.haedal.ondal.hoj;

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
 * 랭킹 API (HojRankingController) - 푼 문제 수 desc → 먼저 도달한 사람 → 이름, 동점은 같은 순위, 0개 제외 (docs hoj/api.md 4절).
 * 엔진 = FakeJudgeEngine(echo): 기대 출력 = 입력이면 ACCEPTED, `judge: WA` 면 WRONG_ANSWER.
 */
class HojRankingApiTest extends ApiTestSupport {

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

    private long createJudgedProblem(String title) throws Exception {
        long id = createProblem(title);
        enableEchoJudge(id);
        return id;
    }

    private void submit(long problemId, String loginId, String code) throws Exception {
        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", code, "language", "C"))))
                .andExpect(status().isCreated());
    }

    @Test
    void 푼_문제_수로_줄_세우고_동점은_같은_순위_먼저_도달한_사람이_앞_0개는_제외() throws Exception {
        long a = createJudgedProblem("A");
        long b = createJudgedProblem("B");
        submit(a, "s1", "int main(){}");
        submit(a, "s2", "int main(){}");
        submit(b, "s1", "int main(){}");   // s1 이 2문제에 먼저 도달
        submit(b, "s2", "int main(){}");
        submit(a, "s3", "int main(){}");
        submit(a, "s3", "int main(){} // 다시");   // 같은 문제를 다시 맞혀도 1개
        submit(a, "s4", "int main(){} // judge: WA");   // 0개 - 목록에 없다

        mockMvc.perform(get("/api/hoj/ranking").session(login.member("s3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].user.name").value("s1"))
                .andExpect(jsonPath("$.items[0].user.loginId").doesNotExist())
                .andExpect(jsonPath("$.items[0].solvedCount").value(2))
                .andExpect(jsonPath("$.items[0].submissionCount").value(2))
                .andExpect(jsonPath("$.items[0].lastSolvedAt").exists())
                .andExpect(jsonPath("$.items[1].rank").value(1))
                .andExpect(jsonPath("$.items[1].user.name").value("s2"))
                .andExpect(jsonPath("$.items[2].rank").value(3))
                .andExpect(jsonPath("$.items[2].user.name").value("s3"))
                .andExpect(jsonPath("$.items[2].solvedCount").value(1))
                .andExpect(jsonPath("$.items[2].submissionCount").value(2))
                .andExpect(jsonPath("$.me.rank").value(3))
                .andExpect(jsonPath("$.me.solvedCount").value(1));

        // 푼 문제가 없는 요청자는 me 가 null
        mockMvc.perform(get("/api/hoj/ranking").session(login.member("s4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me").doesNotExist());
        mockMvc.perform(get("/api/hoj/ranking"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 분반_필터는_그_소속만으로_다시_줄_세운다_size_상한() throws Exception {
        long cohortId = createCohort("A반", "op1");
        enrollStudent(cohortId, "s1");
        long a = createJudgedProblem("A");
        long b = createJudgedProblem("B");
        submit(a, "s2", "int main(){}");   // s2 는 미소속 - 전체 1위
        submit(b, "s2", "int main(){}");
        submit(a, "s1", "int main(){}");

        mockMvc.perform(get("/api/hoj/ranking").session(login.member("s1")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].user.name").value("s2"))
                .andExpect(jsonPath("$.me.rank").value(2));
        mockMvc.perform(get("/api/hoj/ranking").param("cohortId", String.valueOf(cohortId)).session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].rank").value(1))
                .andExpect(jsonPath("$.items[0].user.name").value("s1"))
                .andExpect(jsonPath("$.me.rank").value(1));
        // 그 분반에 없는 요청자는 me 가 null
        mockMvc.perform(get("/api/hoj/ranking").param("cohortId", String.valueOf(cohortId)).session(login.member("s2")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.me").doesNotExist());
        mockMvc.perform(get("/api/hoj/ranking").param("cohortId", "999999").session(login.member("s1")))
                .andExpect(status().isNotFound());
        // size 로 자르되 me 는 전체에서 계산
        mockMvc.perform(get("/api/hoj/ranking").param("size", "1").session(login.member("s1")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].user.name").value("s2"))
                .andExpect(jsonPath("$.me.rank").value(2));
    }
}
