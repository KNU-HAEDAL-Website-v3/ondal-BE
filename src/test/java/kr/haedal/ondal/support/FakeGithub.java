package kr.haedal.ondal.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 가짜 GitHub API - 문제 은행 가져오기가 쓰는 엔드포인트 3개만 흉내낸다 (JDK 내장 HttpServer).
 *
 * - GET /repos/{repo}/commits/{ref}  → Bearer 토큰 검사 후 SHA 문자열
 * - GET /repos/{repo}/zipball/{ref}  → Bearer 토큰 검사 후 302 Location=/codeload/... (실제 GitHub 과 같은 모양)
 * - GET /codeload/...                → zip 바이트. 여기로는 Authorization 이 오면 안 된다(서명 URL) - 받은 헤더를 기록해 테스트가 확인
 * nextStatus 로 다음 API 응답을 401·404 등으로 강제할 수 있다 (한 번 쓰면 200 으로 돌아감).
 */
public final class FakeGithub implements AutoCloseable {

    public static final String TOKEN = "test-token";

    private final HttpServer server;
    private final String repo;
    public volatile byte[] zip = new byte[0];
    public volatile String sha = "0123456789abcdef0123456789abcdef01234567";
    public volatile int nextStatus = 200;
    /** 경로 접두사("commits"/"zipball"/"codeload") → 마지막으로 받은 Authorization 헤더(없으면 "") */
    public final Map<String, String> lastAuthorization = new ConcurrentHashMap<>();

    private FakeGithub(HttpServer server, String repo) {
        this.server = server;
        this.repo = repo;
    }

    public static FakeGithub start(String repo) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeGithub github = new FakeGithub(server, repo);
            server.createContext("/repos/" + repo + "/commits/", github::commits);
            server.createContext("/repos/" + repo + "/zipball/", github::zipball);
            server.createContext("/codeload/", github::codeload);
            server.start();
            return github;
        } catch (IOException e) {
            throw new IllegalStateException("FakeGithub 시작 실패", e);
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void commits(HttpExchange exchange) throws IOException {
        if (!authorized(exchange, "commits")) {
            return;
        }
        if (failIfRequested(exchange)) {
            return;
        }
        respond(exchange, 200, sha.getBytes(StandardCharsets.UTF_8));
    }

    private void zipball(HttpExchange exchange) throws IOException {
        if (!authorized(exchange, "zipball")) {
            return;
        }
        if (failIfRequested(exchange)) {
            return;
        }
        exchange.getResponseHeaders().add("Location", url() + "/codeload/" + repo + "/legacy.zip/main?token=signed");
        respond(exchange, 302, new byte[0]);
    }

    private void codeload(HttpExchange exchange) throws IOException {
        lastAuthorization.put("codeload", header(exchange, "Authorization"));
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        respond(exchange, 200, zip);
    }

    private boolean authorized(HttpExchange exchange, String step) throws IOException {
        String auth = header(exchange, "Authorization");
        lastAuthorization.put(step, auth);
        if (!("Bearer " + TOKEN).equals(auth)) {
            respond(exchange, 401, "{\"message\":\"Bad credentials\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        return true;
    }

    private boolean failIfRequested(HttpExchange exchange) throws IOException {
        int status = nextStatus;
        if (status == 200) {
            return false;
        }
        nextStatus = 200;
        respond(exchange, status, "{\"message\":\"forced\"}".getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
