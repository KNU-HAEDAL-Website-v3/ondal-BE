package kr.haedal.ondal.judge;

import kr.haedal.ondal.judge.engine.Judge0Engine;
import kr.haedal.ondal.judge.engine.JudgeEngineException;
import kr.haedal.ondal.judge.engine.JudgeProperties;
import kr.haedal.ondal.judge.engine.RunOutcome;
import kr.haedal.ondal.judge.engine.RunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Judge0 HTTP 계약 고정 (docs judge/api.md 4절) - 실제 Judge0 없이 MockRestServiceServer 로. 스프링 컨텍스트 없음 */
class Judge0EngineTest {

    private static final String BASE = "http://judge0.test";
    private static final String TOKEN = "secret-token";

    private MockRestServiceServer server;
    private Judge0Engine engine;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        JudgeProperties properties = new JudgeProperties("judge0", true, 2000, 256, 15000, 512, 50, 20,
                Map.of("C", 50, "Python 3", 71),
                new JudgeProperties.Judge0(BASE, TOKEN, 20, 10, 5000, 8));
        engine = new Judge0Engine(properties, builder, JsonMapper.builder().build());
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 배치_제출_후_폴링해_상태를_매핑한다() {
        server.expect(requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Auth-Token", TOKEN))
                .andExpect(jsonPath("$.submissions.length()").value(3))
                .andExpect(jsonPath("$.submissions[0].language_id").value(50))
                .andExpect(jsonPath("$.submissions[0].source_code").value(b64("int main(){}")))
                .andExpect(jsonPath("$.submissions[1].stdin").value(b64("10 20\n")))
                .andExpect(jsonPath("$.submissions[0].cpu_time_limit").value(2.0))
                .andExpect(jsonPath("$.submissions[0].memory_limit").value(262144))
                .andExpect(jsonPath("$.submissions[0].enable_network").value(false))
                .andExpect(jsonPath("$.submissions[0].expected_output").doesNotExist())
                .andRespond(withSuccess("[{\"token\":\"t1\"},{\"token\":\"t2\"},{\"token\":\"t3\"}]", MediaType.APPLICATION_JSON));
        // 1차 폴링: t1 끝, t2·t3 진행 중 → 2차 폴링: 나머지 끝. RestClient 는 템플릿 변수의 쉼표를 %2C 로 인코딩한다(Judge0 는 디코딩해 받는다)
        server.expect(requestTo(startsWith(BASE + "/submissions/batch?tokens=t1%2Ct2%2Ct3")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"submissions\":[" +
                        "{\"token\":\"t1\",\"status\":{\"id\":3},\"stdout\":\"" + b64("3\n") + "\",\"time\":\"0.002\",\"memory\":1234}," +
                        "{\"token\":\"t2\",\"status\":{\"id\":2}}," +
                        "{\"token\":\"t3\",\"status\":{\"id\":1}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/submissions/batch?tokens=t2%2Ct3")))
                .andRespond(withSuccess("{\"submissions\":[" +
                        "{\"token\":\"t2\",\"status\":{\"id\":5},\"stdout\":null,\"time\":\"2.0\",\"memory\":2000}," +
                        "{\"token\":\"t3\",\"status\":{\"id\":11},\"stderr\":\"" + b64("boom") + "\",\"time\":\"0.01\",\"memory\":262144}]}", MediaType.APPLICATION_JSON));

        RunOutcome outcome = engine.run(new RunRequest("C", "int main(){}", List.of("1 2\n", "10 20\n", "x"), 2000, 256));

        server.verify();
        assertThat(outcome.isCompileError()).isFalse();
        assertThat(outcome.runs()).hasSize(3);
        assertThat(outcome.runs().get(0).status()).isEqualTo(RunOutcome.CaseRun.Status.OK);
        assertThat(outcome.runs().get(0).stdout()).isEqualTo("3\n");
        assertThat(outcome.runs().get(0).timeMs()).isEqualTo(2);
        assertThat(outcome.runs().get(0).memoryKb()).isEqualTo(1234);
        assertThat(outcome.runs().get(1).status()).isEqualTo(RunOutcome.CaseRun.Status.TIME_LIMIT);
        // NZEC(11) 이지만 메모리가 제한(256MB = 262144KB)에 닿았으면 MEMORY_LIMIT 로 보정
        assertThat(outcome.runs().get(2).status()).isEqualTo(RunOutcome.CaseRun.Status.MEMORY_LIMIT);
    }

    @Test
    void 컴파일_에러는_케이스_없이_compileOutput_만() {
        server.expect(requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"},{\"token\":\"t2\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/submissions/batch?tokens=")))
                .andRespond(withSuccess("{\"submissions\":[" +
                        "{\"token\":\"t1\",\"status\":{\"id\":6},\"compile_output\":\"" + b64("error: expected ';'") + "\"}," +
                        "{\"token\":\"t2\",\"status\":{\"id\":6},\"compile_output\":\"" + b64("error: expected ';'") + "\"}]}", MediaType.APPLICATION_JSON));

        RunOutcome outcome = engine.run(new RunRequest("C", "int main(){", List.of("1", "2"), 2000, 256));

        assertThat(outcome.isCompileError()).isTrue();
        assertThat(outcome.compileOutput()).contains("expected ';'");
        assertThat(outcome.runs()).isEmpty();
    }

    @Test
    void 인증_실패는_재시도_불가_서버_장애는_재시도_가능() {
        server.expect(requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Unauthorized\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> engine.run(new RunRequest("C", "x", List.of("1"), 2000, 256)))
                .isInstanceOf(JudgeEngineException.class)
                .satisfies(e -> assertThat(((JudgeEngineException) e).isRetryable()).isFalse());

        server.reset();
        server.expect(requestTo(BASE + "/submissions/batch?base64_encoded=true"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"queue is full\"}").contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> engine.run(new RunRequest("C", "x", List.of("1"), 2000, 256)))
                .isInstanceOf(JudgeEngineException.class)
                .satisfies(e -> assertThat(((JudgeEngineException) e).isRetryable()).isTrue());
    }

    @Test
    void 매핑_없는_언어는_재시도_불가_예외() {
        assertThatThrownBy(() -> engine.run(new RunRequest("Rust", "x", List.of("1"), 2000, 256)))
                .isInstanceOf(JudgeEngineException.class)
                .hasMessageContaining("Rust");
    }
}
