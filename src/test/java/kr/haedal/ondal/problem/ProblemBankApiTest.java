package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 문제 은행 지원 (V9) - 난이도·허용 언어(문제별 제출 제한)·번들 가져오기(관리자). 2026-09-19 PM */
class ProblemBankApiTest extends ApiTestSupport {

    private Map<String, Object> problemBody(String title, Integer difficulty, List<String> languages) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", title + " 본문");
        if (difficulty != null) body.put("difficulty", difficulty);
        if (languages != null) body.put("allowedLanguages", languages);
        return body;
    }

    private Map<String, Object> importItem(int problemNo, String title, List<String> tags, List<Map<String, Object>> testCases) {
        Map<String, Object> item = new HashMap<>();
        item.put("problemNo", problemNo);
        item.put("title", title);
        item.put("description", "# " + title + "\n\n본문");
        item.put("difficulty", 3);
        item.put("allowedLanguages", List.of("C"));
        item.put("tags", tags);
        item.put("timeLimitMs", 2000);
        item.put("memoryLimitMb", 256);
        item.put("testCases", testCases);
        return item;
    }

    private static Map<String, Object> testCase(String input, String output, boolean isPublic) {
        return Map.of("input", input, "expectedOutput", output, "isPublic", isPublic);
    }

    /** 연습 제출은 테스트케이스가 있는 문제에만 가능(없으면 409) - 케이스 하나를 넣어 자동 채점 문제로 만든다 */
    private void enableJudge(long problemId) throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("testCases", List.of(testCase("1\n", "1\n", true)));
        config.put("rejudge", false);
        mockMvc.perform(put("/api/problems/{id}/judge", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(config)))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("난이도·허용 언어")
    class DifficultyAndLanguages {

        @Test
        void 등록_수정_조회에_실린다() throws Exception {
            createCohort("C언어", "op1");
            MvcResult created = mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(problemBody("포인터", 7, List.of("C", "Python 3", "C")))))   // 중복은 한 번만
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.difficulty").value(7))
                    .andExpect(jsonPath("$.allowedLanguages", contains("C", "Python 3")))
                    .andReturn();
            long id = readJson(created).get("id").asLong();

            mockMvc.perform(get("/api/problems").session(login.member("anyone")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].difficulty").value(7))
                    .andExpect(jsonPath("$[0].allowedLanguages", contains("C", "Python 3")));

            // 수정에서 비우면 제한 없음·미지정
            mockMvc.perform(put("/api/problems/" + id).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(problemBody("포인터", null, List.of()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.difficulty").doesNotExist())
                    .andExpect(jsonPath("$.allowedLanguages", hasSize(0)));
        }

        @Test
        void 범위_밖_난이도와_지원하지_않는_언어는_400() throws Exception {
            mockMvc.perform(post("/api/problems").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("x", 26, null))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/problems").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("x", 0, null))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/problems").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("x", 3, List.of("Rust")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("Rust")));
        }
    }

    @Nested
    @DisplayName("허용 언어 밖 제출은 400")
    class LanguageRestriction {

        @Test
        void 연습_제출_허용_언어_밖이면_400_안이면_201() throws Exception {
            MvcResult created = mockMvc.perform(post("/api/problems").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("C 로만", 2, List.of("C")))))
                    .andExpect(status().isCreated())
                    .andReturn();
            long id = readJson(created).get("id").asLong();
            enableJudge(id);

            mockMvc.perform(post("/api/problems/{id}/submissions", id).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("codeText", "print(1)", "language", "Python 3"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("C")));
            mockMvc.perform(post("/api/problems/{id}/submissions", id).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("codeText", "int main(){return 0;}", "language", "C"))))
                    .andExpect(status().isCreated());
        }

        @Test
        void 제한_없는_문제는_어느_언어든_201() throws Exception {
            long id = createProblem("자유");
            enableJudge(id);
            mockMvc.perform(post("/api/problems/{id}/submissions", id).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("codeText", "print(1)", "language", "Python 3"))))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("POST /api/problems/import - 번들 가져오기 (관리자)")
    class Import {

        @Test
        void 관리자가_올리면_문제_태그_테스트케이스가_한꺼번에_생긴다() throws Exception {
            Map<String, Object> bundle = Map.of("problems", List.of(
                    importItem(2001, "두 수의 합", List.of("C언어", "구현"),
                            List.of(testCase("1 2\n", "3\n", true), testCase("5 5\n", "10\n", false))),
                    importItem(2002, "거꾸로 출력", List.of("구현"), List.of())));

            mockMvc.perform(post("/api/problems/import").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(bundle)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.created").value(2))
                    .andExpect(jsonPath("$.updated").value(0))
                    .andExpect(jsonPath("$.skipped").value(0))
                    .andExpect(jsonPath("$.createdTags", containsInAnyOrder("C언어", "구현")))
                    .andExpect(jsonPath("$.problemNos", contains(2001, 2002)));

            mockMvc.perform(get("/api/problems").session(login.member("anyone")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].problemNo").value(2001))
                    .andExpect(jsonPath("$[0].judgeEnabled").value(true))
                    .andExpect(jsonPath("$[0].difficulty").value(3))
                    .andExpect(jsonPath("$[0].allowedLanguages", contains("C")))
                    .andExpect(jsonPath("$[0].tags[*].name", containsInAnyOrder("C언어", "구현")))
                    .andExpect(jsonPath("$[1].judgeEnabled").value(false));
            mockMvc.perform(get("/api/tags").session(login.member("anyone")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));

            // 공개 케이스만 예시로 보인다
            MvcResult list = mockMvc.perform(get("/api/problems").session(login.admin())).andReturn();
            long id = readJson(list).get(0).get("id").asLong();
            mockMvc.perform(get("/api/problems/{id}/judge", id).session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.testCases", hasSize(2)))
                    .andExpect(jsonPath("$.timeLimitMs").value(2000));
        }

        @Test
        void 같은_번호는_기본_건너뛰고_overwrite_면_덮어쓴다() throws Exception {
            Map<String, Object> first = Map.of("problems", List.of(importItem(2001, "처음 제목", List.of("구현"), List.of())));
            mockMvc.perform(post("/api/problems/import").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(first)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.created").value(1));

            Map<String, Object> changed = importItem(2001, "바뀐 제목", List.of("구현", "수학"), List.of(testCase("1\n", "1\n", true)));
            changed.put("difficulty", 9);
            mockMvc.perform(post("/api/problems/import").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("problems", List.of(changed)))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.skipped").value(1))
                    .andExpect(jsonPath("$.created").value(0));
            mockMvc.perform(get("/api/problems").session(login.admin()))
                    .andExpect(jsonPath("$[0].title").value("처음 제목"))
                    .andExpect(jsonPath("$[0].difficulty").value(3));

            mockMvc.perform(post("/api/problems/import").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("problems", List.of(changed), "overwrite", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updated").value(1))
                    .andExpect(jsonPath("$.createdTags", contains("수학")));
            mockMvc.perform(get("/api/problems").session(login.admin()))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].title").value("바뀐 제목"))
                    .andExpect(jsonPath("$[0].difficulty").value(9))
                    .andExpect(jsonPath("$[0].judgeEnabled").value(true))
                    .andExpect(jsonPath("$[0].tags", hasSize(2)));
        }

        @Test
        void 운영진은_403_번들_안_중복_번호는_400_하나라도_실패하면_전부_되돌림() throws Exception {
            createCohort("C언어", "op1");
            Map<String, Object> bundle = Map.of("problems", List.of(importItem(2001, "x", List.of(), List.of())));
            mockMvc.perform(post("/api/problems/import").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(bundle)))
                    .andExpect(status().isForbidden());

            Map<String, Object> dup = Map.of("problems", List.of(
                    importItem(2001, "a", List.of(), List.of()), importItem(2001, "b", List.of(), List.of())));
            mockMvc.perform(post("/api/problems/import").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json(dup)))
                    .andExpect(status().isBadRequest());

            Map<String, Object> badLanguage = importItem(2002, "c", List.of(), List.of());
            badLanguage.put("allowedLanguages", List.of("Rust"));
            mockMvc.perform(post("/api/problems/import").session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("problems", List.of(importItem(2001, "a", List.of(), List.of()), badLanguage)))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/problems").session(login.admin()))
                    .andExpect(jsonPath("$", hasSize(0)));   // 2001 도 들어가지 않았다 - 한 트랜잭션
        }
    }
}
