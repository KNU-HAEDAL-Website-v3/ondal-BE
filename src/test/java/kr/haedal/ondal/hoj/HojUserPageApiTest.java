package kr.haedal.ondal.hoj;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
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
 * 사용자 페이지 API (HojUserController) - 통계·언어·푼 문제·시도 중·태그 숙련도·잔디·최근 제출·순위 (docs hoj/api.md 3절).
 * 엔진 = FakeJudgeEngine(echo): 기대 출력 = 입력이면 ACCEPTED, `judge: WA` 면 WRONG_ANSWER.
 */
class HojUserPageApiTest extends ApiTestSupport {

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

    private long createTag(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tags").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long createTaggedProblem(String title, Integer difficulty, List<Long> tagIds) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("tagIds", tagIds);
        if (difficulty != null) {
            body.put("difficulty", difficulty);
        }
        MvcResult result = mockMvc.perform(post("/api/problems").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = readJson(result).get("id").asLong();
        enableEchoJudge(id);
        return id;
    }

    private void submit(long problemId, String loginId, String code, String language) throws Exception {
        mockMvc.perform(post("/api/problems/{id}/submissions", problemId).session(login.member(loginId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("codeText", code, "language", language))))
                .andExpect(status().isCreated());
    }

    @Test
    void 통계_언어_푼_문제_시도_중_태그_숙련도_잔디_최근_제출_순위() throws Exception {
        long impl = createTag("구현");
        long math = createTag("수학");
        createTag("쓰이지 않는 태그");
        long a = createTaggedProblem("A", 3, List.of(impl));           // #1000
        long b = createTaggedProblem("B", 6, List.of(impl, math));     // #1001
        long c = createTaggedProblem("C", null, List.of());            // #1002

        submit(a, "s1", "int main(){}", "C");
        submit(b, "s1", "print(1) # judge: WA", "Python 3");   // 시도 중
        submit(c, "s1", "print(1)", "Python 3");
        submit(a, "s2", "int main(){}", "C");                  // s2 는 1문제 - s1 이 1위
        long s1Id = login.memberUser("s1").getId();
        String todayKst = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

        mockMvc.perform(get("/api/hoj/users/{id}", s1Id).session(login.member("s2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(s1Id))
                .andExpect(jsonPath("$.user.name").value("s1"))
                .andExpect(jsonPath("$.user.title").value("일반 수강생"))
                .andExpect(jsonPath("$.user.loginId").doesNotExist())
                .andExpect(jsonPath("$.joinedAt").exists())
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.stats.solvedCount").value(2))
                .andExpect(jsonPath("$.stats.attemptedCount").value(1))
                .andExpect(jsonPath("$.stats.submissionCount").value(3))
                .andExpect(jsonPath("$.stats.acceptedCount").value(2))
                .andExpect(jsonPath("$.stats.acceptedRate").value(66))
                .andExpect(jsonPath("$.languages", hasSize(2)))
                .andExpect(jsonPath("$.languages[0].language").value("Python 3"))
                .andExpect(jsonPath("$.languages[0].count").value(2))
                .andExpect(jsonPath("$.languages[1].language").value("C"))
                .andExpect(jsonPath("$.languages[1].count").value(1))
                .andExpect(jsonPath("$.solvedProblems", hasSize(2)))
                .andExpect(jsonPath("$.solvedProblems[0].id").value(a))
                .andExpect(jsonPath("$.solvedProblems[0].problemNo").value(1000))
                .andExpect(jsonPath("$.solvedProblems[0].title").value("A"))
                .andExpect(jsonPath("$.solvedProblems[0].difficulty").value(3))
                .andExpect(jsonPath("$.solvedProblems[1].id").value(c))
                .andExpect(jsonPath("$.solvedProblems[1].difficulty").doesNotExist())
                .andExpect(jsonPath("$.attemptedProblems", hasSize(1)))
                .andExpect(jsonPath("$.attemptedProblems[0].id").value(b))
                .andExpect(jsonPath("$.tagStats", hasSize(2)))               // 문제 없는 태그는 생략, 이름순
                .andExpect(jsonPath("$.tagStats[0].tag.name").value("구현"))
                .andExpect(jsonPath("$.tagStats[0].solved").value(1))
                .andExpect(jsonPath("$.tagStats[0].total").value(2))
                .andExpect(jsonPath("$.tagStats[1].tag.name").value("수학"))
                .andExpect(jsonPath("$.tagStats[1].solved").value(0))
                .andExpect(jsonPath("$.tagStats[1].total").value(1))
                .andExpect(jsonPath("$.activity", hasSize(1)))
                .andExpect(jsonPath("$.activity[0].date").value(todayKst))
                .andExpect(jsonPath("$.activity[0].count").value(3))
                .andExpect(jsonPath("$.recentSubmissions", hasSize(3)))
                .andExpect(jsonPath("$.recentSubmissions[0].problem.id").value(c))
                .andExpect(jsonPath("$.recentSubmissions[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.recentSubmissions[1].verdict").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.recentSubmissions[0].codeText").doesNotExist());

        // 활동이 없는 사람 - 순위 null, 정답률 null, 목록은 빈 배열
        long s3Id = login.memberUser("s3").getId();
        mockMvc.perform(get("/api/hoj/users/{id}", s3Id).session(login.member("s3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.stats.solvedCount").value(0))
                .andExpect(jsonPath("$.stats.submissionCount").value(0))
                .andExpect(jsonPath("$.stats.acceptedRate").doesNotExist())
                .andExpect(jsonPath("$.languages", hasSize(0)))
                .andExpect(jsonPath("$.solvedProblems", hasSize(0)))
                .andExpect(jsonPath("$.tagStats", hasSize(2)))
                .andExpect(jsonPath("$.activity", hasSize(0)))
                .andExpect(jsonPath("$.recentSubmissions", hasSize(0)));

        mockMvc.perform(get("/api/hoj/users/{id}", 999_999).session(login.member("s1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/hoj/users/{id}", s1Id))
                .andExpect(status().isUnauthorized());
    }
}
