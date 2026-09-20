package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import kr.haedal.ondal.support.FakeGithub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "깃허브에서 문제 가져오기" - 서버가 문제 은행 레포의 zip 을 받아 problems/* 를 읽어 넣는다 (docs 결정 11 보완).
 * GitHub 은 FakeGithub(JDK HttpServer) 이 대신한다. 설정(api-url·토큰)이 다른 테스트와 달라 이 클래스만 별도 스프링 컨텍스트.
 */
@DisplayName("POST /api/problems/import/github - 깃허브에서 문제 가져오기 (관리자)")
class ProblemBankGithubApiTest extends ApiTestSupport {

    static final String REPO = "org/bank";
    static final FakeGithub GITHUB = FakeGithub.start(REPO);

    @DynamicPropertySource
    static void problemBank(DynamicPropertyRegistry registry) {
        registry.add("ondal.problem-bank.github.api-url", GITHUB::url);
        registry.add("ondal.problem-bank.github.repo", () -> REPO);
        registry.add("ondal.problem-bank.github.ref", () -> "main");
        registry.add("ondal.problem-bank.github.token", () -> FakeGithub.TOKEN);
        registry.add("ondal.problem-bank.async", () -> "false");   // 같은 스레드에서 끝까지 - POST 응답이 곧 최종 상태
    }

    @BeforeEach
    void bundle() {
        GITHUB.zip = zipOf(sampleRepo());
        GITHUB.nextStatus = 200;
        GITHUB.lastAuthorization.clear();
    }

    @Test
    void 관리자만_설정을_보고_가져올_수_있다() throws Exception {
        mockMvc.perform(get("/api/problems/import/github").session(login.member("op1")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/problems/import/github").session(login.member("op1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.repo").value(REPO))
                .andExpect(jsonPath("$.ref").value("main"));
    }

    @Test
    void 레포의_problems_폴더를_읽어_문제_태그_테스트케이스를_만든다() throws Exception {
        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.fetchMs").isNumber())
                .andExpect(jsonPath("$.importMs").isNumber())
                .andExpect(jsonPath("$.requestedBy").value("관리자"))
                .andExpect(jsonPath("$.outcome.repo").value(REPO))
                .andExpect(jsonPath("$.outcome.commitSha").value(GITHUB.sha))
                .andExpect(jsonPath("$.outcome.problemsInRepo").value(2))
                .andExpect(jsonPath("$.outcome.result.created").value(2))
                .andExpect(jsonPath("$.outcome.result.updated").value(0))
                .andExpect(jsonPath("$.outcome.result.skipped").value(0))
                .andExpect(jsonPath("$.outcome.result.createdTags", containsInAnyOrder("구현", "사칙연산", "C언어")));

        // 화면이 폴링하는 상태 조회도 같은 내용
        mockMvc.perform(get("/api/problems/import/github/status").session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.outcome.commitSha").value(GITHUB.sha));

        // 토큰은 GitHub API 에만 - 서명된 다운로드 주소(codeload)에는 넘기지 않는다
        assertThat(GITHUB.lastAuthorization.get("commits")).isEqualTo("Bearer " + FakeGithub.TOKEN);
        assertThat(GITHUB.lastAuthorization.get("zipball")).isEqualTo("Bearer " + FakeGithub.TOKEN);
        assertThat(GITHUB.lastAuthorization.get("codeload")).isEmpty();

        MvcResult list = mockMvc.perform(get("/api/problems").session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].problemNo").value(2001))
                .andExpect(jsonPath("$[0].title").value("두 수의 합"))
                .andExpect(jsonPath("$[0].difficulty").value(1))
                .andExpect(jsonPath("$[1].problemNo").value(2002))
                .andExpect(jsonPath("$[1].allowedLanguages[0]").value("C"))
                .andReturn();
        long first = readJson(list).get(0).get("id").asLong();

        // 본문은 CRLF → LF, 앞뒤 공백 제거 (레포 tools/build.py 와 같은 규칙)
        mockMvc.perform(get("/api/problems/{id}", first).session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("## 문제\n\nA+B 를 출력한다."));

        // tests/01·02 → 테스트케이스 2개, public.txt 의 01 만 공개 예시
        mockMvc.perform(get("/api/problems/{id}/judge", first).session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testCases", hasSize(2)))
                .andExpect(jsonPath("$.timeLimitMs").value(1000));
        mockMvc.perform(get("/api/problems/{id}/judge/samples", first).session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.samples", hasSize(1)))
                .andExpect(jsonPath("$.samples[0].input").value("1 2\n"));
    }

    @Test
    void solutions_폴더의_sol_확장자_파일을_정답_코드로_넣는다_운영진만_본다() throws Exception {
        Map<String, String> files = sampleRepo();   // 2001 에 solutions/sol.py 가 이미 있다
        files.put("problems/2001-a-plus-b/solutions/sol.cpp", "int main(){}\r\n");
        files.put("problems/2001-a-plus-b/solutions/sol.txt", "확장자를 모르면 무시");
        files.put("problems/2001-a-plus-b/solutions/sol.java", "   ");   // 빈 파일도 무시
        GITHUB.zip = zipOf(files);

        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("DONE"));
        MvcResult list = mockMvc.perform(get("/api/problems").session(login.admin())).andReturn();
        long first = readJson(list).get(0).get("id").asLong();

        mockMvc.perform(get("/api/problems/{id}/solutions", first).session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].language").value("C++"))
                .andExpect(jsonPath("$[0].codeText").value("int main(){}\n"))   // CRLF → LF
                .andExpect(jsonPath("$[1].language").value("Python 3"))
                .andExpect(jsonPath("$[1].codeText").value("print(sum(map(int, input().split())))"))
                .andExpect(jsonPath("$[1].updatedBy.name").value("관리자"));
        mockMvc.perform(get("/api/problems/{id}", first).session(login.admin()))
                .andExpect(jsonPath("$.solutionLanguages", contains("C++", "Python 3")));
        mockMvc.perform(get("/api/problems/{id}", first).session(login.member("student")))
                .andExpect(jsonPath("$.solutionLanguages", hasSize(0)));
        mockMvc.perform(get("/api/problems/{id}/solutions", first).session(login.member("student")))
                .andExpect(status().isForbidden());
    }

    @Test
    void 다시_가져오면_같은_번호는_건너뛰고_overwrite_면_갱신한다() throws Exception {
        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome.result.created").value(0))
                .andExpect(jsonPath("$.outcome.result.skipped").value(2));

        mockMvc.perform(post("/api/problems/import/github").param("overwrite", "true").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.overwrite").value(true))
                .andExpect(jsonPath("$.outcome.result.updated").value(2))
                .andExpect(jsonPath("$.outcome.result.skipped").value(0));
    }

    @Test
    void 토큰이_거부되면_FAILED_상태에_원인_안내() throws Exception {
        GITHUB.nextStatus = 401;
        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("PROBLEM_BANK_FETCH_FAILED"))
                .andExpect(jsonPath("$.error.message", containsString("토큰")));

        mockMvc.perform(get("/api/problems").session(login.admin()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 레포에_문제_폴더가_없으면_FAILED() throws Exception {
        GITHUB.zip = zipOf(Map.of("README.md", "빈 레포", "tools/build.py", "print()"));
        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.error.message", containsString("meta.json")));
    }

    @Test
    void 레포_파일이_규칙을_어기면_어느_문제인지_알려준다() throws Exception {
        Map<String, String> files = sampleRepo();
        files.put("problems/2002-c-only/meta.json", "{\"problemNo\": 2002, \"title\": \"\", \"tags\": [\"C언어\"]}");
        GITHUB.zip = zipOf(files);
        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message", containsString("2002")));
    }

    // ---- 레포 흉내 - ondal-problems 의 폴더 규칙 ---------------------------------------------------

    static Map<String, String> sampleRepo() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", "# 문제 은행");
        files.put("tools/build.py", "print('ignored')");
        files.put("problems/README.md", "problems/ 바로 아래 파일은 문제가 아니다");
        files.put("problems/2001-a-plus-b/meta.json",
                "{\"problemNo\": 2001, \"title\": \" 두 수의 합 \", \"difficulty\": 1, \"allowedLanguages\": [], \"tags\": [\"구현\", \"사칙연산\"], \"timeLimitMs\": 1000, \"memoryLimitMb\": 256, \"extra\": \"ignored\"}");
        files.put("problems/2001-a-plus-b/problem.md", "## 문제\r\n\r\nA+B 를 출력한다.\r\n\r\n");
        files.put("problems/2001-a-plus-b/gen.py", "def cases(): return []");
        files.put("problems/2001-a-plus-b/solutions/sol.py", "print(sum(map(int, input().split())))");
        files.put("problems/2001-a-plus-b/tests/01.in", "1 2\n");
        files.put("problems/2001-a-plus-b/tests/01.out", "3\n");
        files.put("problems/2001-a-plus-b/tests/02.in", "5 5\n");
        files.put("problems/2001-a-plus-b/tests/02.out", "10\n");
        files.put("problems/2001-a-plus-b/tests/public.txt", "01\n");
        files.put("problems/2002-c-only/meta.json",
                "{\"problemNo\": 2002, \"title\": \"[C언어] 포인터\", \"difficulty\": 7, \"allowedLanguages\": [\"C\"], \"tags\": [\"C언어\"]}");
        files.put("problems/2002-c-only/problem.md", "## 문제\n\n포인터.");
        return files;
    }

    /** GitHub zipball 처럼 최상위 폴더 "<소유자>-<레포>-<sha7>/" 아래에 파일을 넣는다 */
    static byte[] zipOf(Map<String, String> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("org-bank-0123456/"));
            zip.closeEntry();
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry("org-bank-0123456/" + file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }
}
