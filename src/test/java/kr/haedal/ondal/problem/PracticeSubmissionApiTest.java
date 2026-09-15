package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

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
 * HOJ 연습 제출 API (PracticeSubmissionController) - 분반과 무관하게 문제를 풀어 채점받는다 (V7).
 *
 * 엔진 = FakeJudgeEngine(test 기본): 지시 주석이 없으면 입력을 그대로 돌려준다(echo).
 * 그래서 기대 출력을 입력과 같게 두면 ACCEPTED, 다르게 두면 WRONG_ANSWER. 워커는 동기(ondal.judge.async=false).
 */
class PracticeSubmissionApiTest extends ApiTestSupport {

    private static Map<String, Object> testCase(String input, String expected, boolean isPublic) {
        Map<String, Object> tc = new HashMap<>();
        tc.put("input", input);
        tc.put("expectedOutput", expected);
        tc.put("isPublic", isPublic);
        return tc;
    }

    /** 문제에 테스트케이스를 붙인다 - 기대 출력 = 입력이면 echo 엔진 기준 ACCEPTED */
    private void putConfig(long problemId, List<Map<String, Object>> cases) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("timeLimitMs", 3000);
        body.put("memoryLimitMb", 256);
        body.put("testCases", cases);
        body.put("rejudge", false);
        mockMvc.perform(put("/api/problems/{id}/judge", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private long submit(long problemId, String loginId, String code, String language) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/problems/{id}/submissions", problemId)
                        .session(login.member(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", code, "language", language))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Test
    void 채점_기준이_없는_문제는_409() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("기준 없는 문제");
        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member("anyone"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", "print(1)", "language", "Python 3"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void 분반에_속하지_않아도_풀고_채점받는다() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");
        putConfig(problemId, List.of(testCase("1 2\n", "1 2\n", true)));

        // outsider 는 어떤 분반에도 속하지 않는다 - HOJ 는 그래도 열린다
        MvcResult created = mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member("outsider"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", "int main(){}", "language", "C"))))
                .andExpect(status().isCreated())
                // 채점은 커밋 이후에 돈다 - 생성 응답은 대기 상태다 (과제 제출과 같은 규약, judge/api.md 2절)
                .andExpect(jsonPath("$.judge.status").value("PENDING"))
                // 연습에는 마감도 코멘트도 없다
                .andExpect(jsonPath("$.late").value(false))
                .andExpect(jsonPath("$.comment").doesNotExist())
                .andReturn();
        long submissionId = readJson(created).get("id").asLong();

        mockMvc.perform(get("/api/problems/{id}/submissions/{sid}", problemId, submissionId)
                        .session(login.member("outsider")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judge.status").value("DONE"))
                .andExpect(jsonPath("$.judge.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.judge.passedCases").value(1));
    }

    @Test
    void 기대_출력과_다르면_WRONG_ANSWER() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");
        putConfig(problemId, List.of(testCase("1 2\n", "3\n", true)));   // echo 엔진은 "1 2" 를 돌려준다

        long submissionId = submit(problemId, "s1", "int main(){}", "C");
        mockMvc.perform(get("/api/problems/{id}/submissions/{sid}", problemId, submissionId).session(login.member("s1")))
                .andExpect(jsonPath("$.judge.verdict").value("WRONG_ANSWER"));
    }

    @Test
    void 지원하지_않는_언어는_400_코드가_비면_400() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");
        putConfig(problemId, List.of(testCase("1 2\n", "1 2\n", true)));

        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", "fn main(){}", "language", "Rust"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", " ", "language", "C"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 내_기록은_최신_먼저_남의_제출은_404() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");
        putConfig(problemId, List.of(testCase("1 2\n", "1 2\n", true)));

        long first = submit(problemId, "s1", "int main(){} // 1", "C");
        long second = submit(problemId, "s1", "int main(){} // 2", "C");
        submit(problemId, "s2", "int main(){} // other", "C");

        mockMvc.perform(get("/api/problems/{id}/submissions/my", problemId).session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[0].verdict").value("ACCEPTED"));

        mockMvc.perform(get("/api/problems/{id}/submissions/{sid}", problemId, second).session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeText").value("int main(){} // 2"));

        // 남의 연습 제출은 존재를 드러내지 않는다
        mockMvc.perform(get("/api/problems/{id}/submissions/{sid}", problemId, second).session(login.member("s2")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 연습_제출은_과제_현황판이나_내_과제_기록에_섞이지_않는다() throws Exception {
        long cohortId = createCohort("C언어", "op1");
        enrollStudent(cohortId, "s1");
        long problemId = createProblem("A+B");
        putConfig(problemId, List.of(testCase("1 2\n", "1 2\n", true)));

        MvcResult assigned = mockMvc.perform(post("/api/cohorts/{id}/assignments", cohortId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("problemId", problemId,
                                "dueAt", java.time.Instant.now().plusSeconds(86400).toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        long assignmentId = readJson(assigned).get("id").asLong();

        submit(problemId, "s1", "int main(){} // 연습", "C");

        // 과제 쪽 기록은 비어 있어야 한다 - 같은 문제라도 연습은 과제 제출이 아니다
        mockMvc.perform(get("/api/cohorts/{cid}/assignments/{aid}/submissions/my", cohortId, assignmentId)
                        .session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/cohorts/{cid}/assignments/{aid}", cohortId, assignmentId).session(login.member("s1")))
                .andExpect(jsonPath("$.myStatus").value("NOT_SUBMITTED"));
    }

    @Test
    void 맞히면_문제_목록에_해결_표시가_붙는다() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");
        putConfig(problemId, List.of(testCase("1 2\n", "1 2\n", true)));

        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(jsonPath("$.solved").value(false));
        submit(problemId, "s1", "int main(){}", "C");
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(jsonPath("$.solved").value(true));
        // 남의 해결 여부는 내 화면에 영향을 주지 않는다
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s2")))
                .andExpect(jsonPath("$.solved").value(false));
    }
}
