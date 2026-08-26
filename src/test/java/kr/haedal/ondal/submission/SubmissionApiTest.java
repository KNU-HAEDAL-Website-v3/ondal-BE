package kr.haedal.ondal.submission;

import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 제출 API (SubmissionController #18~#22) + 과제 응답 확장(myStatus·submissionCount)·삭제 연쇄 */
class SubmissionApiTest extends ApiTestSupport {

    private static final Instant FUTURE_DUE = Instant.now().plus(7, ChronoUnit.DAYS);
    private static final Instant PAST_DUE = Instant.now().minus(3, ChronoUnit.DAYS);

    @Autowired private SubmissionRepository submissionRepository;

    // ---- 슬라이스 고유 픽스처 (support/는 PM 파일 - 여기 private 헬퍼로) ----------------------

    private long createAssignment(long cohortId, Instant dueAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/assignments", cohortId)
                        .session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "과제", "description", "설명", "dueAt", dueAt.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private void updateDueAt(long cohortId, long assignmentId, Instant dueAt) throws Exception {
        mockMvc.perform(put("/api/cohorts/{id}/assignments/{aid}", cohortId, assignmentId)
                        .session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "과제", "description", "설명", "dueAt", dueAt.toString()))))
                .andExpect(status().isOk());
    }

    private MockMultipartFile requestPart(Map<String, Object> body) throws Exception {
        return new MockMultipartFile("request", "request", "application/json",
                json(body).getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile zipFile(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/zip", content);
    }

    private Map<String, Object> codeBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("codeText", "print('hello')");
        body.put("language", "Python 3");
        return body;
    }

    /** 코드 제출 후 submission id를 돌려준다 */
    private long submitCode(long cohortId, long assignmentId, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                        .file(requestPart(codeBody()))
                        .session(session))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Nested
    @DisplayName("인증과 권한 - 역할 x 엔드포인트")
    class Authorization {

        @Test
        void 미로그인이면_401() throws Exception {
            long id = createCohort("C언어", "op1");
            long aid = createAssignment(id, FUTURE_DUE);
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 비소속_부원은_403() throws Exception {
            long id = createCohort("C언어", "op1");
            long aid = createAssignment(id, FUTURE_DUE);
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody()))
                            .session(login.member("outsider")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid)
                            .session(login.member("outsider")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 수강생은_제출과_자기_이력_상세를_볼_수_있다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);

            MvcResult created = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody()))
                            .session(login.member("s1")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/submissions/")))
                    .andExpect(jsonPath("$.late").value(false))
                    .andExpect(jsonPath("$.user.name").value("s1"))
                    .andReturn();
            long submissionId = readJson(created).get("id").asLong();

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid)
                            .session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, submissionId)
                            .session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codeText").value("print('hello')"));
        }

        @Test
        void 수강생_현황판은_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.member("s1")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 수강생이_타인_제출물을_보면_404() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long aid = createAssignment(id, FUTURE_DUE);
            long submissionId = submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, submissionId)
                            .session(login.member("s2")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}/file", id, aid, submissionId)
                            .session(login.member("s2")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 운영진과_비소속_관리자는_타인_제출물_열람_가능() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            long submissionId = submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, submissionId)
                            .session(login.member("op1")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, submissionId)
                            .session(login.admin()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.admin()))
                    .andExpect(status().isOk());
        }

        @Test
        void 운영진도_제출할_수_있지만_현황판_행에는_없다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("op1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].user.name").value("s1"));
        }
    }

    @Nested
    @DisplayName("스코프 조회 - 손자 리소스는 체인 전부 검사")
    class Scope {

        @Test
        void 같은_분반_다른_과제의_submissionId는_404() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long a1 = createAssignment(id, FUTURE_DUE);
            long a2 = createAssignment(id, FUTURE_DUE);
            long submissionId = submitCode(id, a1, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, a2, submissionId)
                            .session(login.member("s1")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 다른_분반_경로로는_제출_접근_불가_404() throws Exception {
            long a = createCohort("A반", "op1");
            long b = createCohort("B반", "op2");
            enrollStudent(b, "s1");
            long bAssignment = createAssignment(b, FUTURE_DUE);
            long submissionId = submitCode(b, bAssignment, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", a, bAssignment, submissionId)
                            .session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 없는_submissionId는_404() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, 999_999)
                            .session(login.member("s1")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("제출 형식 검증 - 본문(코드/파일 택1) + 링크, 최소 1개")
    class Validation {

        private long cohortId;
        private long assignmentId;

        private MockHttpSession student() throws Exception {
            cohortId = createCohort("C언어", "op1");
            enrollStudent(cohortId, "s1");
            assignmentId = createAssignment(cohortId, FUTURE_DUE);
            return login.member("s1");
        }

        @Test
        void 코드와_파일_동시는_400() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(codeBody()))
                            .file(zipFile("solution.zip", new byte[]{1}))
                            .session(s1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 본문도_링크도_없으면_400() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of()))
                            .session(s1))
                    .andExpect(status().isBadRequest());
            // 빈 문자열은 없는 것으로 취급한다
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("codeText", " ", "linkUrl", "")))
                            .session(s1))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 코드만_파일만_링크만_코드와링크_전부_성공() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("codeText", "int main(){}")))
                            .session(s1))
                    .andExpect(status().isCreated());
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of()))
                            .file(zipFile("solution.zip", new byte[]{1, 2}))
                            .session(s1))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fileName").value("solution.zip"))
                    .andExpect(jsonPath("$.fileSize").value(2));
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("linkUrl", "https://github.com/s1/hw")))
                            .session(s1))
                    .andExpect(status().isCreated());
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("codeText", "int main(){}", "linkUrl", "https://github.com/s1/hw")))
                            .session(s1))
                    .andExpect(status().isCreated());
        }

        @Test
        void zip_외_확장자는_400() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of()))
                            .file(zipFile("virus.exe", new byte[]{1}))
                            .session(s1))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of()))
                            .file(zipFile("readme.txt", new byte[]{1}))
                            .session(s1))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 이십MB_초과_파일은_400() throws Exception {
            MockHttpSession s1 = student();
            byte[] tooBig = new byte[20 * 1024 * 1024 + 1];
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of()))
                            .file(zipFile("big.zip", tooBig))
                            .session(s1))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 파일_제출에_언어를_실으면_400() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("language", "C")))
                            .file(zipFile("solution.zip", new byte[]{1}))
                            .session(s1))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 길이_제한_초과는_400() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("codeText", "a".repeat(100_001))))
                            .session(s1))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("codeText", "code", "language", "가".repeat(31))))
                            .session(s1))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(requestPart(Map.of("linkUrl", "https://" + "a".repeat(2048))))
                            .session(s1))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void request_파트가_없으면_400() throws Exception {
            MockHttpSession s1 = student();
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                            .file(zipFile("solution.zip", new byte[]{1}))
                            .session(s1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("지각과 상태 계산 - 저장하지 않고 이력에서 계산")
    class LateAndStatus {

        @Test
        void 마감_후_제출은_차단하지_않고_지각으로_표시() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, PAST_DUE);

            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody()))
                            .session(login.member("s1")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.late").value(true));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myStatus").value("LATE"));
        }

        @Test
        void 마감_전_제출은_SUBMITTED() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myStatus").value("SUBMITTED"));
        }

        @Test
        void 마감_내_제출_후_마감_후_재제출이면_SUBMITTED_EXTRA() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);

            MvcResult first = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody()))
                            .session(login.member("s1")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Instant firstSubmittedAt = Instant.parse(readJson(first).get("submittedAt").asString());

            // 마감을 첫 제출 시각으로 당긴다(제출 시각 == 마감은 마감 내) - 이후 제출은 전부 지각
            updateDueAt(id, aid, firstSubmittedAt);
            submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myStatus").value("SUBMITTED_EXTRA"));
        }

        @Test
        void 마감을_연장하면_지각이_제출로_재계산된다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, PAST_DUE);
            submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$.myStatus").value("LATE"));

            updateDueAt(id, aid, FUTURE_DUE);
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$.myStatus").value("SUBMITTED"));
        }

        @Test
        void 무제출은_NOT_SUBMITTED_비소속_관리자는_null() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$.myStatus").value("NOT_SUBMITTED"));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.admin()))
                    .andExpect(jsonPath("$.myStatus", nullValue()));
        }

        @Test
        void submissionCount는_운영진과_관리자에게만_보인다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("s1"));
            submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$.submissionCount", nullValue()));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("op1")))
                    .andExpect(jsonPath("$.submissionCount").value(2));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.admin()))
                    .andExpect(jsonPath("$.submissionCount").value(2));
        }
    }

    @Nested
    @DisplayName("재제출 이력 - append-only, 최신이 대표")
    class History {

        @Test
        void 세_번_제출하면_이력_3건_최신순_코드_전문은_제외() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("s1"));
            submitCode(id, aid, login.member("s1"));
            long last = submitCode(id, aid, login.member("s1"));
            submitCode(id, aid, login.member("s2")); // 타인 제출은 내 이력에 섞이지 않는다

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid)
                            .session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].id").value(last))
                    .andExpect(jsonPath("$[0].codeText").doesNotExist());
        }
    }

    @Nested
    @DisplayName("현황판 - 현재 수강생 명단 기준")
    class StatusBoard {

        @Test
        void 미제출자_포함_상태와_건수와_최근_제출_시각() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("s1"));
            long latest = submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].user.name").value("s1"))
                    .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                    .andExpect(jsonPath("$[0].submissionCount").value(2))
                    .andExpect(jsonPath("$[0].lastSubmittedAt").isNotEmpty())
                    .andExpect(jsonPath("$[0].latestSubmissionId").value(latest))
                    .andExpect(jsonPath("$[1].user.name").value("s2"))
                    .andExpect(jsonPath("$[1].status").value("NOT_SUBMITTED"))
                    .andExpect(jsonPath("$[1].submissionCount").value(0))
                    .andExpect(jsonPath("$[1].lastSubmittedAt", nullValue()))
                    .andExpect(jsonPath("$[1].latestSubmissionId", nullValue()));
        }

        @Test
        void 마감_지난_과제는_지각으로_집계() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, PAST_DUE);
            submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.member("op1")))
                    .andExpect(jsonPath("$[0].status").value("LATE"));
        }

        @Test
        void 소속_해제된_학생은_행에서_빠지고_제출_데이터는_남는다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("s1"));

            mockMvc.perform(delete("/api/cohorts/{id}/students/{loginId}", id, "s1").session(login.admin()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].user.name").value("s2"));
            assertThat(submissionRepository.count()).isEqualTo(1); // 이력은 유지 (schema.md 3절)
        }
    }

    @Nested
    @DisplayName("파일 업로드와 다운로드")
    class FileFlow {

        @Test
        void 업로드한_zip을_원본_파일명으로_그대로_받는다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            byte[] content = "PK-테스트-압축-내용".getBytes(StandardCharsets.UTF_8);

            MvcResult created = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(Map.of()))
                            .file(zipFile("과제제출.zip", content))
                            .session(login.member("s1")))
                    .andExpect(status().isCreated())
                    .andReturn();
            long submissionId = readJson(created).get("id").asLong();

            MvcResult downloaded = mockMvc.perform(
                            get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}/file", id, aid, submissionId)
                                    .session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                    .andReturn();
            assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(content);
        }

        @Test
        void 코드_제출에는_다운로드할_파일이_없다_404() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            long submissionId = submitCode(id, aid, login.member("s1"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}/file", id, aid, submissionId)
                            .session(login.member("s1")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("보관 분반 - 제출은 409, 열람은 유지")
    class ArchivedCohort {

        @Test
        void 보관되면_제출_409_열람_200_해제하면_다시_제출_가능() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            long submissionId = submitCode(id, aid, login.member("s1"));
            archiveCohort(id);

            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody()))
                            .session(login.member("s1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid)
                            .session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, submissionId)
                            .session(login.member("s1")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid)
                            .session(login.member("op1")))
                    .andExpect(status().isOk());

            restoreCohort(id);
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(codeBody()))
                            .session(login.member("s1")))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("과제 삭제 연쇄 - 파일 → 이력 → 과제")
    class DeleteCascade {

        @Test
        void 과제를_지우면_제출_이력과_디스크_파일까지_지워진다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id, FUTURE_DUE);
            submitCode(id, aid, login.member("s1"));
            mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", id, aid)
                            .file(requestPart(Map.of()))
                            .file(zipFile("solution.zip", new byte[]{1, 2, 3}))
                            .session(login.member("s1")))
                    .andExpect(status().isCreated());

            List<Submission> submissions = submissionRepository.findAllByAssignmentId(aid);
            String storedPath = submissions.stream()
                    .filter(Submission::hasFile)
                    .findFirst().orElseThrow()
                    .getStoredPath();
            Path storedFile = Path.of("build/test-uploads").resolve(storedPath);
            assertThat(Files.exists(storedFile)).isTrue();

            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("op1")))
                    .andExpect(status().isNoContent());

            assertThat(submissionRepository.count()).isZero();
            assertThat(Files.exists(storedFile)).isFalse();
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, aid).session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }
    }
}
