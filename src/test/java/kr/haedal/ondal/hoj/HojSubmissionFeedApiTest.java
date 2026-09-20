package kr.haedal.ondal.hoj;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 채점 현황 피드 API (HojSubmissionController) - HOJ 연습 제출만, id desc, 커서·필터 (docs hoj/api.md 2절).
 * 엔진 = FakeJudgeEngine(echo): 기대 출력 = 입력이면 ACCEPTED, `judge: WA` 면 WRONG_ANSWER.
 */
class HojSubmissionFeedApiTest extends ApiTestSupport {

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

    private long submit(long problemId, String loginId, String code, String language) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", code, "language", language))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Test
    void 연습_제출만_최신_먼저_과제_제출은_섞이지_않는다() throws Exception {
        long cohortId = createCohort("C언어", "op1");
        enrollStudent(cohortId, "s1");
        long a = createProblem("A+B");
        long b = createProblem("A-B");
        enableEchoJudge(a);
        enableEchoJudge(b);

        // 같은 문제를 과제로도 낸다 - 과제 제출은 피드에 없어야 한다 (PM 결정 13: 채점 현황은 HOJ 만)
        MvcResult assigned = mockMvc.perform(post("/api/cohorts/{id}/assignments", cohortId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("problemId", a, "dueAt", Instant.now().plusSeconds(86400).toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        long assignmentId = readJson(assigned).get("id").asLong();
        mockMvc.perform(multipart("/api/cohorts/{cid}/assignments/{aid}/submissions", cohortId, assignmentId)
                        .file(new MockMultipartFile("request", "request", "application/json",
                                json(Map.of("type", "CODE", "codeText", "int main(){} // 과제", "language", "C")).getBytes(StandardCharsets.UTF_8)))
                        .session(login.member("s1")))
                .andExpect(status().isCreated());

        long first = submit(a, "s1", "int main(){}", "C");
        long second = submit(a, "s2", "print(1) # judge: WA", "Python 3");
        long third = submit(b, "s1", "print(1)", "Python 3");

        mockMvc.perform(get("/api/hoj/submissions").session(login.member("anyone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.items[0].problem.id").value(b))
                .andExpect(jsonPath("$.items[0].problem.problemNo").value(1001))
                .andExpect(jsonPath("$.items[0].problem.title").value("A-B"))
                .andExpect(jsonPath("$.items[0].user.name").value("s1"))
                .andExpect(jsonPath("$.items[0].user.loginId").doesNotExist())
                .andExpect(jsonPath("$.items[0].language").value("Python 3"))
                .andExpect(jsonPath("$.items[0].judgeStatus").value("DONE"))
                .andExpect(jsonPath("$.items[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.items[0].passedCases").value(1))
                .andExpect(jsonPath("$.items[0].totalCases").value(1))
                .andExpect(jsonPath("$.items[0].submittedAt").exists())
                .andExpect(jsonPath("$.items[0].codeText").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(second))
                .andExpect(jsonPath("$.items[1].verdict").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.items[2].id").value(first))
                .andExpect(jsonPath("$.nextBeforeId").doesNotExist());

        mockMvc.perform(get("/api/hoj/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 커서로_이어_읽는다_size_는_200_까지() throws Exception {
        long a = createProblem("A+B");
        enableEchoJudge(a);
        long s1 = submit(a, "s1", "int main(){} // 1", "C");
        long s2 = submit(a, "s1", "int main(){} // 2", "C");
        long s3 = submit(a, "s1", "int main(){} // 3", "C");
        long s4 = submit(a, "s1", "int main(){} // 4", "C");

        mockMvc.perform(get("/api/hoj/submissions").param("size", "3").session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").value(s4))
                .andExpect(jsonPath("$.items[2].id").value(s2))
                .andExpect(jsonPath("$.nextBeforeId").value(s2));
        mockMvc.perform(get("/api/hoj/submissions").param("size", "3").param("beforeId", String.valueOf(s2)).session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(s1))
                .andExpect(jsonPath("$.nextBeforeId").doesNotExist());
        // 상한을 넘는 size 는 잘라서 처리 - 오류가 아니다
        mockMvc.perform(get("/api/hoj/submissions").param("size", "9999").session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)));
        mockMvc.perform(get("/api/hoj/submissions").param("size", "abc").session(login.member("s1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 문제_사용자_판정_언어로_거른다() throws Exception {
        long a = createProblem("A+B");
        long b = createProblem("A-B");
        enableEchoJudge(a);
        enableEchoJudge(b);
        submit(a, "s1", "int main(){}", "C");
        long wrong = submit(a, "s2", "print(1) # judge: WA", "Python 3");
        submit(b, "s1", "print(1)", "Python 3");
        long s1Id = login.memberUser("s1").getId();

        mockMvc.perform(get("/api/hoj/submissions").param("problemId", String.valueOf(a)).session(login.member("s1")))
                .andExpect(jsonPath("$.items", hasSize(2)));
        mockMvc.perform(get("/api/hoj/submissions").param("userId", String.valueOf(s1Id)).session(login.member("s2")))
                .andExpect(jsonPath("$.items", hasSize(2)));
        mockMvc.perform(get("/api/hoj/submissions").param("verdict", "WRONG_ANSWER").session(login.member("s1")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(wrong));
        mockMvc.perform(get("/api/hoj/submissions").param("language", "Python 3").session(login.member("s1")))
                .andExpect(jsonPath("$.items", hasSize(2)));
        mockMvc.perform(get("/api/hoj/submissions").param("problemId", String.valueOf(b)).param("verdict", "ACCEPTED")
                        .param("language", "Python 3").param("userId", String.valueOf(s1Id)).session(login.member("s1")))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(get("/api/hoj/submissions").param("verdict", "FOO").session(login.member("s1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
