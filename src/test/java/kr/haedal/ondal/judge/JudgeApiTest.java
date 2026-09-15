package kr.haedal.ondal.judge;

import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.judge.repository.TestCaseRepository;
import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자동 채점 API (JudgeController #47~#51) + 제출 응답의 judge 필드 + 파이프라인.
 * 엔진 = FakeJudgeEngine(test 기본): 지시 주석 없으면 입력을 echo → 기대 출력을 입력과 같게 두면 ACCEPTED. 워커는 동기(ondal.judge.async=false)
 */
class JudgeApiTest extends ApiTestSupport {

    private static final Instant FUTURE_DUE = Instant.now().plus(7, ChronoUnit.DAYS);

    @Autowired TestCaseRepository testCaseRepository;
    @Autowired JudgeResultRepository judgeResultRepository;

    /** 배정(과제) id -> 배정된 문제 id. V7 이후 채점 설정은 문제 스코프라 경로를 만들 때 필요하다 */
    private final Map<Long, Long> problemOfAssignment = new HashMap<>();

    // ---- 픽스처 --------------------------------------------------------------------------

    /** 문제를 만들고 분반에 배정한다 - 과제 = 배정이므로 두 단계 (V7) */
    private long createAssignment(long cohortId) throws Exception {
        long assignmentId = createAssignmentOf(cohortId, "A+B", null, FUTURE_DUE);
        MvcResult detail = mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", cohortId, assignmentId)
                        .session(login.admin()))
                .andExpect(status().isOk())
                .andReturn();
        problemOfAssignment.put(assignmentId, readJson(detail).get("problemId").asLong());
        return assignmentId;
    }

    /** 채점 설정 경로 - 배정이 아니라 문제에 달린다. 인자를 그대로 둔 것은 호출부를 흔들지 않기 위해서 */
    private String judgePath(long cohortId, long assignmentId) {
        return "/api/problems/" + problemOfAssignment.get(assignmentId) + "/judge";
    }

    private long problemOf(long assignmentId) {
        return problemOfAssignment.get(assignmentId);
    }

    /** 재채점만 분반(과제) 스코프에 남았다 - "내 반 제출만 다시 돌린다"는 분반 운영 동작이라서 */
    private String rejudgePath(long cohortId, long assignmentId) {
        return "/api/cohorts/" + cohortId + "/assignments/" + assignmentId + "/judge/rejudge";
    }

    private static Map<String, Object> testCase(String input, String expected, boolean isPublic) {
        Map<String, Object> tc = new HashMap<>();
        tc.put("input", input);
        tc.put("expectedOutput", expected);
        tc.put("isPublic", isPublic);
        return tc;
    }

    /** 기본 2케이스(공개 1·비공개 1) - echo 엔진에 맞춰 기대 출력 = 입력 */
    private Map<String, Object> defaultConfig(boolean rejudge) {
        Map<String, Object> body = new HashMap<>();
        body.put("timeLimitMs", 3000);
        body.put("memoryLimitMb", 256);
        body.put("testCases", List.of(testCase("1 2\n", "1 2\n", true), testCase("10 20\n", "10 20\n", false)));
        body.put("rejudge", rejudge);
        return body;
    }

    private void putConfig(long cohortId, long assignmentId, MockHttpSession session, Map<String, Object> body) throws Exception {
        mockMvc.perform(put(judgePath(cohortId, assignmentId)).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    private long submitCode(long cohortId, long assignmentId, MockHttpSession session, String code, String language) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "CODE");
        body.put("codeText", code);
        body.put("language", language);
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json", json(body).getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                        .file(request).session(session))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long submitLink(long cohortId, long assignmentId, MockHttpSession session) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "LINK");
        body.put("linkUrls", List.of("https://example.com/solution"));
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json", json(body).getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                        .file(request).session(session))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private String submissionPath(long cohortId, long assignmentId, long submissionId) {
        return "/api/cohorts/" + cohortId + "/assignments/" + assignmentId + "/submissions/" + submissionId;
    }

    @Nested
    @DisplayName("채점 설정 #47 #48")
    class Config {

        @Test
        void 운영진이_저장하고_조회한다_학생은_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);

            // 케이스 없음 = 자동 채점 아님, 과제 응답 judgeEnabled false
            mockMvc.perform(get(judgePath(id, aid)).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.engineAvailable").value(true))
                    .andExpect(jsonPath("$.timeLimitMs").value(2000))
                    .andExpect(jsonPath("$.memoryLimitMb").value(256))
                    .andExpect(jsonPath("$.maxTestCases").value(50))
                    .andExpect(jsonPath("$.languages[0]").value("C"))
                    .andExpect(jsonPath("$.testCases.length()").value(0))
                    .andExpect(jsonPath("$.affectedSubmissions").value(0));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$.judgeEnabled").value(false));

            mockMvc.perform(get(judgePath(id, aid)).session(login.member("s1"))).andExpect(status().isForbidden());
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(defaultConfig(false))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(defaultConfig(false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.timeLimitMs").value(3000))
                    .andExpect(jsonPath("$.testCases.length()").value(2))
                    .andExpect(jsonPath("$.testCases[0].position").value(0))
                    .andExpect(jsonPath("$.testCases[0].isPublic").value(true))
                    .andExpect(jsonPath("$.testCases[1].input").value("10 20\n"))
                    .andExpect(jsonPath("$.testCases[1].isPublic").value(false))
                    .andExpect(jsonPath("$.rejudgeQueued").value(0));
            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].judgeEnabled").value(true));

            // 통째 교체 - 1개로 줄이면 1개만 남는다
            Map<String, Object> one = defaultConfig(false);
            one.put("testCases", List.of(testCase("7\n", "7\n", true)));
            one.put("timeLimitMs", null);
            putConfig(id, aid, login.member("op1"), one);
            mockMvc.perform(get(judgePath(id, aid)).session(login.admin()))
                    .andExpect(jsonPath("$.testCases.length()").value(1))
                    .andExpect(jsonPath("$.testCases[0].input").value("7\n"))
                    .andExpect(jsonPath("$.timeLimitMs").value(2000));   // null → 기본값
            assertThat(testCaseRepository.countByProblemId(problemOf(aid))).isEqualTo(1);

            // 0개 = 해제
            Map<String, Object> none = defaultConfig(false);
            none.put("testCases", List.of());
            putConfig(id, aid, login.member("op1"), none);
            mockMvc.perform(get(judgePath(id, aid)).session(login.member("op1"))).andExpect(jsonPath("$.enabled").value(false));
        }

        @Test
        void 검증_400_과_보관_409() throws Exception {
            long id = createCohort("C언어", "op1");
            long aid = createAssignment(id);

            Map<String, Object> tooMany = defaultConfig(false);
            List<Map<String, Object>> cases = new ArrayList<>();
            for (int i = 0; i < 51; i++) cases.add(testCase("" + i, "" + i, false));
            tooMany.put("testCases", cases);
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(tooMany)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));

            Map<String, Object> badTime = defaultConfig(false);
            badTime.put("timeLimitMs", 99999);
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(badTime)))
                    .andExpect(status().isBadRequest());

            Map<String, Object> badMemory = defaultConfig(false);
            badMemory.put("memoryLimitMb", 8);
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(badMemory)))
                    .andExpect(status().isBadRequest());

            Map<String, Object> nullInput = defaultConfig(false);
            Map<String, Object> broken = new HashMap<>();
            broken.put("expectedOutput", "x");
            nullInput.put("testCases", List.of(broken));
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(nullInput)))
                    .andExpect(status().isBadRequest());

            archiveCohort(id);
            // V7: 채점 설정은 문제의 것이라 분반 보관과 무관하다 - 보관된 분반의 과제가 가리키는 문제도 계속 손볼 수 있다
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(defaultConfig(false))))
                    .andExpect(status().isOk());
            mockMvc.perform(get(judgePath(id, aid)).session(login.member("op1"))).andExpect(status().isOk());   // 조회는 유지
            restoreCohort(id);
            putConfig(id, aid, login.member("op1"), defaultConfig(false));
        }
    }

    @Nested
    @DisplayName("채점 파이프라인 - 제출 응답의 judge")
    class Pipeline {

        @Test
        void 코드_제출은_채점되고_학생은_공개_케이스만_본다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long aid = createAssignment(id);
            putConfig(id, aid, login.member("op1"), defaultConfig(false));

            // 제출 직후 응답은 PENDING (채점은 커밋 뒤) → 조회하면 DONE
            Map<String, Object> body = new HashMap<>();
            body.put("type", "CODE");
            body.put("codeText", "#include <stdio.h>\nint main(){}");
            body.put("language", "C");
            MockMultipartFile request = new MockMultipartFile("request", "request", "application/json", json(body).getBytes(StandardCharsets.UTF_8));
            MvcResult created = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid).file(request).session(login.member("s1")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.judge.status").value("PENDING"))
                    .andExpect(jsonPath("$.judge.verdict", nullValue()))
                    .andReturn();
            long sid = readJson(created).get("id").asLong();

            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.judge.status").value("DONE"))
                    .andExpect(jsonPath("$.judge.verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.judge.passedCases").value(2))
                    .andExpect(jsonPath("$.judge.totalCases").value(2))
                    .andExpect(jsonPath("$.judge.maxTimeMs").value(3))
                    .andExpect(jsonPath("$.judge.judgedAt").isNotEmpty())
                    .andExpect(jsonPath("$.judge.cases.length()").value(2))
                    .andExpect(jsonPath("$.judge.cases[0].isPublic").value(true))
                    .andExpect(jsonPath("$.judge.cases[0].input").value("1 2\n"))
                    .andExpect(jsonPath("$.judge.cases[0].actualOutput").value("1 2\n"))
                    .andExpect(jsonPath("$.judge.cases[1].isPublic").value(false))
                    .andExpect(jsonPath("$.judge.cases[1].input", nullValue()))
                    .andExpect(jsonPath("$.judge.cases[1].actualOutput", nullValue()))
                    .andExpect(jsonPath("$.judge.cases[1].verdict").value("ACCEPTED"));
            // 운영진도 비공개 입력은 못 본다(같은 규칙) - 케이스 원문은 #47
            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("op1")))
                    .andExpect(jsonPath("$.judge.cases[1].input", nullValue()));

            // 이력·현황판·타인 404
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].judgeStatus").value("DONE"))
                    .andExpect(jsonPath("$[0].verdict").value("ACCEPTED"));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid).session(login.member("op1")))
                    .andExpect(jsonPath("$[0].latestVerdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$[0].latestJudgeStatus").value("DONE"))
                    .andExpect(jsonPath("$[1].latestVerdict", nullValue()));
            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("s2"))).andExpect(status().isNotFound());

            // 2번째 케이스만 틀림 → WRONG_ANSWER, 1/2. 공개 케이스 0번은 그대로 통과
            long wa = submitCode(id, aid, login.member("s1"), "// judge: WA@1\nint main(){}", "C");
            mockMvc.perform(get(submissionPath(id, aid, wa)).session(login.member("s1")))
                    .andExpect(jsonPath("$.judge.verdict").value("WRONG_ANSWER"))
                    .andExpect(jsonPath("$.judge.passedCases").value(1))
                    .andExpect(jsonPath("$.judge.cases[0].verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.judge.cases[1].verdict").value("WRONG_ANSWER"));
            // 최신 제출이 대표 - 현황판 판정이 바뀐다
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid).session(login.member("op1")))
                    .andExpect(jsonPath("$[0].latestVerdict").value("WRONG_ANSWER"));

            long tle = submitCode(id, aid, login.member("s1"), "// judge: TLE\nint main(){}", "C");
            mockMvc.perform(get(submissionPath(id, aid, tle)).session(login.member("s1")))
                    .andExpect(jsonPath("$.judge.verdict").value("TIME_LIMIT"))
                    .andExpect(jsonPath("$.judge.maxTimeMs").value(3000));

            long ce = submitCode(id, aid, login.member("s1"), "// judge: CE\nint main(){", "C");
            mockMvc.perform(get(submissionPath(id, aid, ce)).session(login.member("s1")))
                    .andExpect(jsonPath("$.judge.verdict").value("COMPILE_ERROR"))
                    .andExpect(jsonPath("$.judge.compileOutput").isNotEmpty())
                    .andExpect(jsonPath("$.judge.cases.length()").value(0));

            long broken = submitCode(id, aid, login.member("s1"), "// judge: ERROR\nint main(){}", "C");
            mockMvc.perform(get(submissionPath(id, aid, broken)).session(login.member("s1")))
                    .andExpect(jsonPath("$.judge.status").value("ERROR"))
                    .andExpect(jsonPath("$.judge.verdict").value("JUDGE_ERROR"));

            // LINK 제출·지원하지 않는 언어
            long link = submitLink(id, aid, login.member("s2"));
            mockMvc.perform(get(submissionPath(id, aid, link)).session(login.member("s2")))
                    .andExpect(jsonPath("$.judge", nullValue()));
            Map<String, Object> rust = new HashMap<>();
            rust.put("type", "CODE");
            rust.put("codeText", "fn main(){}");
            rust.put("language", "Rust");
            MockMultipartFile rustPart = new MockMultipartFile("request", "request", "application/json", json(rust).getBytes(StandardCharsets.UTF_8));
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid).file(rustPart).session(login.member("s2")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 케이스_없는_과제의_코드_제출은_채점_대상이_아니다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);
            long sid = submitCode(id, aid, login.member("s1"), "print(1)", "Python 3");
            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("s1")))
                    .andExpect(jsonPath("$.judge", nullValue()));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].judgeStatus", nullValue()))
                    .andExpect(jsonPath("$[0].verdict", nullValue()));
            // 아무 언어로도 제출 가능 (케이스 없음)
            submitCode(id, aid, login.member("s1"), "fn main(){}", "Rust");
        }
    }

    @Nested
    @DisplayName("출제 도구 #49 · 예시 #50 · 재채점 #51 · 삭제 연쇄")
    class Tools {

        @Test
        void 실행은_출력을_돌려주고_기대_출력이_있으면_판정한다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);

            Map<String, Object> fill = new HashMap<>();
            fill.put("language", "C");
            fill.put("sourceCode", "int main(){}");
            fill.put("inputs", List.of("1 2\n", "3 4\n"));
            mockMvc.perform(post(judgePath(id, aid) + "/run").session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(fill)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.compileOutput", nullValue()))
                    .andExpect(jsonPath("$.runs.length()").value(2))
                    .andExpect(jsonPath("$.runs[0].stdout").value("1 2\n"))
                    .andExpect(jsonPath("$.runs[0].verdict", nullValue()))
                    .andExpect(jsonPath("$.runs[1].stdout").value("3 4\n"));

            Map<String, Object> verify = new HashMap<>(fill);
            verify.put("expectedOutputs", List.of("1 2", "wrong"));
            mockMvc.perform(post(judgePath(id, aid) + "/run").session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(verify)))
                    .andExpect(jsonPath("$.runs[0].verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.runs[1].verdict").value("WRONG_ANSWER"));

            Map<String, Object> ce = new HashMap<>(fill);
            ce.put("sourceCode", "// judge: CE\nint main(){");
            mockMvc.perform(post(judgePath(id, aid) + "/run").session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(ce)))
                    .andExpect(jsonPath("$.compileOutput").isNotEmpty())
                    .andExpect(jsonPath("$.runs.length()").value(0));

            Map<String, Object> mismatch = new HashMap<>(fill);
            mismatch.put("expectedOutputs", List.of("only one"));
            mockMvc.perform(post(judgePath(id, aid) + "/run").session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(mismatch)))
                    .andExpect(status().isBadRequest());
            Map<String, Object> rust = new HashMap<>(fill);
            rust.put("language", "Rust");
            mockMvc.perform(post(judgePath(id, aid) + "/run").session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(rust)))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post(judgePath(id, aid) + "/run").session(login.member("s1")).contentType(MediaType.APPLICATION_JSON).content(json(fill)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 예시는_공개_케이스만_소속_누구나() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);
            mockMvc.perform(get(judgePath(id, aid) + "/samples").session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.samples.length()").value(0));
            putConfig(id, aid, login.member("op1"), defaultConfig(false));
            mockMvc.perform(get(judgePath(id, aid) + "/samples").session(login.member("s1")))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.timeLimitMs").value(3000))
                    .andExpect(jsonPath("$.samples.length()").value(1))
                    .andExpect(jsonPath("$.samples[0].input").value("1 2\n"))
                    .andExpect(jsonPath("$.samples[0].expectedOutput").value("1 2\n"))
                    .andExpect(jsonPath("$.languages.length()").value(6));
            // V7: 예시는 문제 스코프 - 분반에 속하지 않은 부원도 HOJ 에서 문제를 보므로 로그인만 되면 열린다
            mockMvc.perform(get(judgePath(id, aid) + "/samples").session(login.member("outsider"))).andExpect(status().isOk());
        }

        @Test
        void 재채점은_새_케이스로_결과를_바꾼다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);
            putConfig(id, aid, login.member("op1"), defaultConfig(false));
            long sid = submitCode(id, aid, login.member("s1"), "int main(){}", "C");
            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("s1"))).andExpect(jsonPath("$.judge.verdict").value("ACCEPTED"));

            // 기대 출력을 바꾸고 rejudge=false 로 저장 → 결과 불변, 대상 건수만
            Map<String, Object> changed = defaultConfig(false);
            changed.put("testCases", List.of(testCase("1 2\n", "different\n", true)));
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(changed)))
                    .andExpect(jsonPath("$.affectedSubmissions").value(1))
                    .andExpect(jsonPath("$.rejudgeQueued").value(0));
            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("s1"))).andExpect(jsonPath("$.judge.verdict").value("ACCEPTED"));

            // rejudge=true → WRONG_ANSWER 로 갱신
            changed.put("rejudge", true);
            mockMvc.perform(put(judgePath(id, aid)).session(login.member("op1")).contentType(MediaType.APPLICATION_JSON).content(json(changed)))
                    .andExpect(jsonPath("$.rejudgeQueued").value(1));
            mockMvc.perform(get(submissionPath(id, aid, sid)).session(login.member("s1")))
                    .andExpect(jsonPath("$.judge.verdict").value("WRONG_ANSWER"))
                    .andExpect(jsonPath("$.judge.totalCases").value(1));

            // #51 명시 재채점 → 202
            mockMvc.perform(post(rejudgePath(id, aid)).session(login.member("op1")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.queued").value(1));
            mockMvc.perform(post(rejudgePath(id, aid)).session(login.member("s1"))).andExpect(status().isForbidden());

            // 케이스 0개 → 409
            Map<String, Object> none = defaultConfig(false);
            none.put("testCases", List.of());
            putConfig(id, aid, login.member("op1"), none);
            mockMvc.perform(post(rejudgePath(id, aid)).session(login.member("op1"))).andExpect(status().isConflict());
        }

        @Test
        void 과제를_지우면_채점_결과는_사라지고_테스트케이스는_문제에_남는다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);
            putConfig(id, aid, login.member("op1"), defaultConfig(false));
            submitCode(id, aid, login.member("s1"), "int main(){}", "C");
            assertThat(testCaseRepository.countByProblemId(problemOf(aid))).isEqualTo(2);
            assertThat(judgeResultRepository.findAllByAssignmentId(aid)).hasSize(1);

            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("op1")))
                    .andExpect(status().isNoContent());
            // 채점 결과는 제출과 함께 사라진다
            assertThat(judgeResultRepository.findAllByAssignmentId(aid)).isEmpty();
            // 테스트케이스는 문제의 것이라 그대로 - 같은 문제를 쓰는 다른 분반의 채점 기준이 사라지면 안 된다 (V7)
            assertThat(testCaseRepository.countByProblemId(problemOf(aid))).isEqualTo(2);
        }
    }
}
