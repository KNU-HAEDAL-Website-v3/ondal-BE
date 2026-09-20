package kr.haedal.ondal.judge;

import kr.haedal.ondal.judge.service.RunRateLimiter;
import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "내 입력으로 실행" API (ProblemRunController) - 로그인 누구나, 입력 1~5개, 사용자당 분당 10회 (docs hoj/api.md 8절).
 * 엔진 = FakeJudgeEngine(echo): 입력이 그대로 stdout 으로 온다.
 */
class ProblemRunApiTest extends ApiTestSupport {

    @Autowired
    private RunRateLimiter rateLimiter;

    /** 한도 카운터는 메모리에 남고 사용자 id 는 테스트마다 1부터 다시 시작한다 - 이전 테스트의 기록을 비운다 */
    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.clear();
    }

    private static Map<String, Object> run(String language, String code, List<String> inputs) {
        Map<String, Object> body = new HashMap<>();
        body.put("language", language);
        body.put("sourceCode", code);
        body.put("inputs", inputs);
        return body;
    }

    @Test
    void 학생도_자기_입력으로_실행하고_출력만_받는다() throws Exception {
        long problemId = createProblem("A+B");   // 테스트케이스가 없어도 실행은 된다 - 판정이 아니라 출력이다
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("Python 3", "print(input())", List.of("1 2", "3")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileOutput").doesNotExist())
                .andExpect(jsonPath("$.runs", hasSize(2)))
                .andExpect(jsonPath("$.runs[0].index").value(0))
                .andExpect(jsonPath("$.runs[0].stdout").value("1 2"))
                .andExpect(jsonPath("$.runs[0].verdict").doesNotExist())   // 기대 출력이 없으니 판정도 없다
                .andExpect(jsonPath("$.runs[0].timeMs").exists())
                .andExpect(jsonPath("$.runs[1].stdout").value("3"));

        // 컴파일 에러는 compileOutput 만
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){} // judge: CE", List.of("1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileOutput", containsString("compile")))
                .andExpect(jsonPath("$.runs", hasSize(0)));

        mockMvc.perform(post("/api/problems/{id}/run", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("1")))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/problems/{id}/run", 999_999).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("1")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 입력_0개_6개_긴_입력_허용_언어_밖은_400() throws Exception {
        long problemId = createProblem("A+B");
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("1", "2", "3", "4", "5", "6")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("x".repeat(10_001))))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("Rust", "fn main(){}", List.of("1")))))
                .andExpect(status().isBadRequest());

        // 문제의 허용 언어 밖
        Map<String, Object> cOnly = new HashMap<>();
        cOnly.put("title", "C 로만");
        cOnly.put("allowedLanguages", List.of("C"));
        MvcResult created = mockMvc.perform(post("/api/problems").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(cOnly)))
                .andExpect(status().isCreated())
                .andReturn();
        long cOnlyId = readJson(created).get("id").asLong();
        mockMvc.perform(post("/api/problems/{id}/run", cOnlyId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("Python 3", "print(1)", List.of("1")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message", containsString("C")));
        mockMvc.perform(post("/api/problems/{id}/run", cOnlyId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("1")))))
                .andExpect(status().isOk());
    }

    @Test
    void 사용자당_분당_10회를_넘기면_429_다른_사용자는_영향_없음() throws Exception {
        long problemId = createProblem("A+B");
        for (int i = 0; i < RunRateLimiter.LIMIT_PER_MINUTE; i++) {
            mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(run("C", "int main(){}", List.of("1")))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("1")))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        mockMvc.perform(post("/api/problems/{id}/run", problemId).session(login.member("s2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(run("C", "int main(){}", List.of("1")))))
                .andExpect(status().isOk());
        // 한도는 실행에만 - 다른 API 는 그대로
        mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                .andExpect(status().isOk());
    }
}
