package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정답 코드(참고 풀이) API (ProblemSolutionController) - 운영진 이상만, 언어별 1건, 통째 교체 (V11, docs hoj/api.md 6절).
 * 학생에게는 존재 자체가 보이지 않는다 - GET/PUT 403 이고 상세의 solutionLanguages 도 [].
 */
class ProblemSolutionApiTest extends ApiTestSupport {

    private static Map<String, Object> solution(String language, String code) {
        return Map.of("language", language, "codeText", code);
    }

    private static Map<String, Object> body(List<Map<String, Object>> solutions) {
        return Map.of("solutions", solutions);
    }

    private Map<String, Object> importItem(int problemNo, List<Map<String, Object>> solutions) {
        Map<String, Object> item = new HashMap<>();
        item.put("problemNo", problemNo);
        item.put("title", "번들 문제 " + problemNo);
        item.put("description", "본문");
        item.put("tags", List.of("구현"));
        item.put("testCases", List.of());
        if (solutions != null) {
            item.put("solutions", solutions);
        }
        return item;
    }

    @Test
    void 학생은_403_상세의_solutionLanguages_도_빈_배열() throws Exception {
        long cohortId = createCohort("C언어", "op1");
        enrollStudent(cohortId, "s1");
        long problemId = createProblem("A+B");
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("Python 3", "print(sum(map(int, input().split())))"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.member("s1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("C", "int main(){}"))))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId))
                .andExpect(status().isUnauthorized());

        // 존재 자체를 숨긴다 - 학생 상세에는 언어 목록도 없다
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutionLanguages", hasSize(0)));
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("op1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutionLanguages", contains("Python 3")));
    }

    @Test
    void 운영진은_저장하고_조회한다_통째_교체_언어_이름순() throws Exception {
        createCohort("C언어", "op1");
        long problemId = createProblem("A+B");

        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.member("op1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("Python 3", "print(1)"), solution("C", "int main(){}"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].language").value("C"))
                .andExpect(jsonPath("$[0].codeText").value("int main(){}"))
                .andExpect(jsonPath("$[0].updatedBy.name").value("op1"))
                .andExpect(jsonPath("$[0].updatedBy.loginId").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[1].language").value("Python 3"));

        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 통째 교체 - 준 목록이 곧 결과
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("Java", "class Main {}"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].language").value("Java"))
                .andExpect(jsonPath("$[0].updatedBy.name").value("관리자"));

        // 빈 배열 = 모두 삭제
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/problems/{id}/solutions", 999_999).session(login.admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 언어_중복_지원하지_않는_언어_7개_초과_빈_코드는_400() throws Exception {
        long problemId = createProblem("A+B");
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("C", "a"), solution("C", "b"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message", containsString("C")));
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("Rust", "fn main(){}"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Rust")));
        List<Map<String, Object>> seven = List.of(solution("C", "1"), solution("C++", "2"), solution("Java", "3"),
                solution("Python 3", "4"), solution("JavaScript", "5"), solution("TypeScript", "6"), solution("C", "7"));
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body(seven))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body(List.of(solution("C", " "))))))
                .andExpect(status().isBadRequest());
        // 실패한 저장은 아무것도 남기지 않는다
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 번들_가져오기의_solutions_는_저장되고_overwrite_면_교체_없으면_그대로() throws Exception {
        mockMvc.perform(post("/api/problems/import").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("problems", List.of(importItem(2001, List.of(solution("Python 3", "print(1)"))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        MvcResult list = mockMvc.perform(get("/api/problems").session(login.admin())).andReturn();
        long problemId = readJson(list).get(0).get("id").asLong();
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].language").value("Python 3"))
                .andExpect(jsonPath("$[0].updatedBy.name").value("관리자"));

        // overwrite - 배열이 있으면 통째 교체
        mockMvc.perform(post("/api/problems/import").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("overwrite", true,
                                "problems", List.of(importItem(2001, List.of(solution("C", "int main(){}"))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].language").value("C"));

        // solutions 필드가 없는 번들(옛 빌드)은 기존 정답 코드를 건드리지 않는다
        mockMvc.perform(post("/api/problems/import").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("overwrite", true, "problems", List.of(importItem(2001, null))))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].language").value("C"));

        // 번들 안 중복 언어는 400 - 한 트랜잭션이라 문제도 안 들어간다
        mockMvc.perform(post("/api/problems/import").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("problems", List.of(importItem(2002, List.of(solution("C", "a"), solution("C", "b"))))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/problems").session(login.admin()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void 문제를_지우면_정답_코드도_함께_지워진다() throws Exception {
        long problemId = createProblem("지울 문제");
        mockMvc.perform(put("/api/problems/{id}/solutions", problemId).session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(List.of(solution("C", "int main(){}"))))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/problems/{id}", problemId).session(login.admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/problems/{id}/solutions", problemId).session(login.admin()))
                .andExpect(status().isNotFound());
    }
}
