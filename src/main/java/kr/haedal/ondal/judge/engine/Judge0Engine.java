package kr.haedal.ondal.judge.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Judge0 CE(1.13) HTTP 엔진 - docs judge/api.md 4절의 요청 규약.
 *  1) POST /submissions/batch?base64_encoded=true (케이스 = 제출 1개씩, batch-size 로 나눔, wait=false) → 토큰
 *  2) GET  /submissions/batch?tokens=..&base64_encoded=true&fields=.. 를 poll-interval 마다, 전부 끝나거나 poll-timeout 까지
 *  3) status → CaseRun.Status 매핑. expected_output 은 보내지 않는다(판정은 서버 비교기)
 * 인증은 X-Auth-Token. 연결 실패·5xx·큐 초과는 재시도 가능, 4xx(인증·형식)는 불가.
 */
@Component
@ConditionalOnProperty(name = JudgeEngineMode.PROPERTY, havingValue = JudgeEngineMode.JUDGE0)
public class Judge0Engine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(Judge0Engine.class);
    private static final String FIELDS = "token,status,stdout,stderr,compile_output,message,time,memory,exit_code";

    private final JudgeProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final boolean configured;

    public Judge0Engine(JudgeProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        JudgeProperties.Judge0 judge0 = properties.judge0();
        this.configured = judge0 != null && judge0.url() != null && !judge0.url().isBlank()
                && judge0.token() != null && !judge0.token().isBlank();
        if (!configured) {
            throw new IllegalStateException("ondal.judge.engine=judge0 에는 ondal.judge.judge0.url 과 token 이 필요합니다 (.env JUDGE0_URL, JUDGE0_TOKEN).");
        }
        this.client = builder
                .baseUrl(judge0.url().replaceAll("/+$", ""))
                .defaultHeader("X-Auth-Token", judge0.token())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("[judge] Judge0 엔진 연결 설정: {} (batch {}, poll {}ms/{}ms)", judge0.url(), judge0.batchSize(), judge0.pollIntervalMs(), judge0.pollTimeoutMs());
    }

    @Override
    public boolean available() {
        return configured;
    }

    /**
     * 기동 직후 연결 확인 - GET /languages 1회. 실패해도 기동은 계속(워커가 재시도한다) - 운영자가 `docker compose logs ondal-be | grep judge` 한 줄로 연결 상태를 본다.
     * 컨테이너 → VM(multipass 브리지) 라우팅이 막혔을 때 이 WARN 이 첫 단서 (docs judge/infra.md 4절-3).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logConnectivity() {
        try {
            String raw = client.get().uri("/languages").retrieve().body(String.class);
            JsonNode languages = raw == null ? null : objectMapper.readTree(raw);
            int count = languages != null && languages.isArray() ? languages.size() : -1;
            log.info("[judge] Judge0 연결 확인 OK - {} 언어 사용 가능 ({})", count, properties.judge0().url());
        } catch (RestClientResponseException e) {
            log.warn("[judge] Judge0 연결은 되지만 응답 {} - 토큰(JUDGE0_TOKEN = judge0.conf AUTHN_TOKEN)을 확인하세요: {}", e.getStatusCode().value(), JudgeResultText.head(e.getResponseBodyAsString(), 200));
        } catch (RuntimeException e) {
            log.warn("[judge] Judge0 연결 실패 - {} ({}). 채점은 PENDING 으로 대기하며 재시도합니다. VM 기동·2358 포트·컨테이너→VM 라우팅(iptables DOCKER-USER) 확인", e.getMessage(), properties.judge0().url());
        }
    }

    @Override
    public RunOutcome run(RunRequest request) {
        Integer languageId = properties.languages() == null ? null : properties.languages().get(request.language());
        if (languageId == null) {
            throw new JudgeEngineException("Judge0 언어 매핑이 없습니다: " + request.language(), false);
        }
        List<String> tokens = submitAll(request, languageId);
        Map<String, JsonNode> results = pollAll(tokens);

        List<RunOutcome.CaseRun> runs = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            JsonNode node = results.get(tokens.get(i));
            if (node == null) {
                runs.add(new RunOutcome.CaseRun(i, RunOutcome.CaseRun.Status.ENGINE_ERROR, "", "폴링 시간 초과", null, null));
                continue;
            }
            int statusId = node.path("status").path("id").asInt(13);
            String compileOutput = decode(node.path("compile_output"));
            if (statusId == 6) {
                // 컴파일 에러는 소스 하나의 문제 - 케이스 전체가 같은 결과. 첫 케이스에서 확정
                return RunOutcome.compileError(compileOutput);
            }
            runs.add(toCaseRun(i, statusId, node, request.memoryLimitMb()));
        }
        return RunOutcome.of(runs);
    }

    // ---- HTTP -----------------------------------------------------------------------------

    private List<String> submitAll(RunRequest request, int languageId) {
        JudgeProperties.Judge0 cfg = properties.judge0();
        int batchSize = Math.max(1, cfg.batchSize());
        List<String> tokens = new ArrayList<>();
        for (int from = 0; from < request.inputs().size(); from += batchSize) {
            List<String> chunk = request.inputs().subList(from, Math.min(from + batchSize, request.inputs().size()));
            ArrayNode submissions = objectMapper.createArrayNode();
            for (String input : chunk) {
                ObjectNode s = submissions.addObject();
                s.put("language_id", languageId);
                s.put("source_code", encode(request.sourceCode()));
                s.put("stdin", encode(input));
                double cpu = request.timeLimitMs() / 1000.0;
                s.put("cpu_time_limit", cpu);
                s.put("cpu_extra_time", 0.5);
                s.put("wall_time_limit", Math.max(1.0, cpu * 2 + 1));   // Judge0 1.13 보안 규칙: wall_time_limit 은 1초 이상
                s.put("memory_limit", request.memoryLimitMb() * 1024);
                s.put("max_processes_and_or_threads", 60);
                s.put("enable_network", false);
                s.put("redirect_stderr_to_stdout", false);
                s.put("max_file_size", 1024);
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.set("submissions", submissions);

            JsonNode response = exchange(() -> client.post()
                    .uri("/submissions/batch?base64_encoded=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class));
            if (response == null || !response.isArray()) {
                throw new JudgeEngineException("Judge0 batch 응답 형식이 다릅니다: " + response, false);
            }
            for (JsonNode item : response) {
                String token = item.path("token").asString(null);
                if (token == null) {
                    // 항목별 검증 실패(예: 큐 초과 "queue is full") - 재시도 가능으로 본다
                    throw new JudgeEngineException("Judge0 가 제출을 거절했습니다: " + item, true);
                }
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Map<String, JsonNode> pollAll(List<String> tokens) {
        JudgeProperties.Judge0 cfg = properties.judge0();
        Map<String, JsonNode> done = new HashMap<>();
        long deadline = System.currentTimeMillis() + cfg.pollTimeoutMs();
        List<String> remaining = new ArrayList<>(tokens);
        while (!remaining.isEmpty() && System.currentTimeMillis() < deadline) {
            String joined = String.join(",", remaining);
            JsonNode response = exchange(() -> client.get()
                    .uri("/submissions/batch?tokens={tokens}&base64_encoded=true&fields={fields}", joined, FIELDS)
                    .retrieve()
                    .body(String.class));
            JsonNode submissions = response == null ? null : response.path("submissions");
            if (submissions != null && submissions.isArray()) {
                for (JsonNode s : submissions) {
                    int statusId = s.path("status").path("id").asInt(0);
                    String token = s.path("token").asString(null);
                    if (token != null && statusId >= 3) {
                        done.put(token, s);
                        remaining.remove(token);
                    }
                }
            }
            if (remaining.isEmpty()) {
                break;
            }
            sleep(cfg.pollIntervalMs());
        }
        return done;
    }

    private JsonNode exchange(java.util.function.Supplier<String> call) {
        try {
            String raw = call.get();
            return raw == null || raw.isBlank() ? null : objectMapper.readTree(raw);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            boolean retryable = status.is5xxServerError() || status.value() == 429;
            throw new JudgeEngineException("Judge0 응답 " + status.value() + ": " + JudgeResultText.head(e.getResponseBodyAsString(), 300), retryable, e);
        } catch (ResourceAccessException e) {
            throw new JudgeEngineException("Judge0 연결 실패: " + e.getMessage(), true, e);
        }
    }

    // ---- 매핑 -------------------------------------------------------------------------------

    private RunOutcome.CaseRun toCaseRun(int index, int statusId, JsonNode node, int memoryLimitMb) {
        String stdout = decode(node.path("stdout"));
        String stderr = decode(node.path("stderr"));
        Integer timeMs = parseSeconds(node.path("time"));
        Integer memoryKb = node.path("memory").isNumber() ? (int) Math.round(node.path("memory").asDouble()) : null;
        RunOutcome.CaseRun.Status status = switch (statusId) {
            case 3, 4 -> RunOutcome.CaseRun.Status.OK;   // 4(Wrong Answer)는 expected_output 을 안 보내므로 나오지 않지만, 나와도 우리 비교기가 판정
            case 5 -> RunOutcome.CaseRun.Status.TIME_LIMIT;
            case 7, 8, 9, 10, 11, 12 -> memoryKb != null && memoryKb >= memoryLimitMb * 1024L
                    ? RunOutcome.CaseRun.Status.MEMORY_LIMIT      // 메모리 초과는 isolate 가 SIGSEGV·NZEC 로 보고하기도 한다
                    : RunOutcome.CaseRun.Status.RUNTIME_ERROR;
            default -> RunOutcome.CaseRun.Status.ENGINE_ERROR;  // 13 Internal Error, 14 Exec Format Error, 그 외
        };
        if (status == RunOutcome.CaseRun.Status.ENGINE_ERROR) {
            String message = node.path("message").isString() ? decode(node.path("message")) : "";
            log.warn("[judge] Judge0 status {} - {}", statusId, message);
            stderr = message;
        }
        return new RunOutcome.CaseRun(index, status, stdout == null ? "" : stdout, stderr == null ? "" : stderr, timeMs, memoryKb);
    }

    /** Judge0 의 time 은 초 단위 문자열("0.002") - ms 로 반올림. 없거나 숫자가 아니면 null */
    private static Integer parseSeconds(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(node.asString()) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String raw = node.asString("");
        if (raw.isEmpty()) {
            return "";
        }
        try {
            return new String(Base64.getMimeDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;   // base64 가 아니면 그대로 (message 필드 등)
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgeEngineException("폴링 중단", true, e);
        }
    }

    /** 로그·예외 메시지용 잘라내기 */
    static final class JudgeResultText {
        static String head(String value, int max) {
            if (value == null) {
                return "";
            }
            return value.length() <= max ? value : value.substring(0, max) + "...";
        }
    }
}
