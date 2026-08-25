package kr.haedal.ondal.assignment;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 과제 API (AssignmentController #13~#17) */
class AssignmentApiTest extends ApiTestSupport {

    private static final Instant FUTURE_DUE = Instant.now().plus(7, ChronoUnit.DAYS);

    // ---- 슬라이스 고유 픽스처 (support/는 PM 파일 - 여기 private 헬퍼로) ----------------------

    private Map<String, Object> assignmentBody(Integer sessionNo, String title) {
        Map<String, Object> body = new HashMap<>();
        if (sessionNo != null) {
            body.put("sessionNo", sessionNo);
        }
        body.put("title", title);
        body.put("description", title + " 설명");
        body.put("dueAt", FUTURE_DUE.toString());
        return body;
    }

    private long createAssignment(long cohortId, Integer sessionNo, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/assignments", cohortId)
                        .session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(assignmentBody(sessionNo, title))))
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
            mockMvc.perform(get("/api/cohorts/{id}/assignments", id))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 비소속_부원은_403() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 수강생은_조회만_가능하고_쓰기는_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long assignmentId = createAssignment(id, 1, "1차시 과제");

            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("s1")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("s1")))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "새 과제"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/cohorts/{id}/assignments/{aid}", id, assignmentId)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "수정"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("s1")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 운영진은_등록_수정_삭제_가능() throws Exception {
            long id = createCohort("C언어", "op1");

            MvcResult created = mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "1차시 과제"))))
                    .andExpect(status().isCreated())
                    .andReturn();
            long assignmentId = readJson(created).get("id").asLong();

            mockMvc.perform(put("/api/cohorts/{id}/assignments/{aid}", id, assignmentId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(2, "수정된 과제"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("수정된 과제"));

            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("op1")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void 비소속_관리자는_통과() throws Exception {
            long id = createCohort("C언어", "op1");
            createAssignment(id, 1, "1차시 과제");
            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("스코프 조회 - 다른 분반의 과제는 존재를 드러내지 않는다")
    class Scope {

        @Test
        void 다른_분반의_과제는_GET_PUT_DELETE_모두_404() throws Exception {
            long a = createCohort("A반", "op1");
            long b = createCohort("B반", "op2");
            long bAssignment = createAssignment(b, 1, "B반 과제");

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", a, bAssignment).session(login.member("op1")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/cohorts/{id}/assignments/{aid}", a, bAssignment)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "탈취 시도"))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", a, bAssignment).session(login.member("op1")))
                    .andExpect(status().isNotFound());

            // B반 과제는 그대로 남아 있다
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", b, bAssignment).session(login.member("op2")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("B반 과제"));
        }

        @Test
        void 없는_과제id는_404() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, 999_999).session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 없는_분반은_관리자_404_부원_403() throws Exception {
            mockMvc.perform(get("/api/cohorts/{id}/assignments", 999_999).session(login.admin()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/cohorts/{id}/assignments", 999_999).session(login.member("nobody")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        void 경로의_cohortId가_숫자가_아니면_400() throws Exception {
            mockMvc.perform(get("/api/cohorts/abc/assignments").session(login.admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 제목_공백이나_200자_초과는_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, " "))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "가".repeat(201)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 마감_누락이면_400() throws Exception {
            long id = createCohort("C언어", "op1");
            Map<String, Object> body = assignmentBody(1, "마감 없는 과제");
            body.remove("dueAt");
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 차시_0이면_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(0, "0차시 과제"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 차시_없이_등록하면_차시_밖_과제() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(null, "설문 과제"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionNo", nullValue()));
        }

        @Test
        void 과거_마감도_허용된다() throws Exception {
            long id = createCohort("C언어", "op1");
            Map<String, Object> body = assignmentBody(1, "이미 마감된 과제");
            body.put("dueAt", Instant.now().minus(3, ChronoUnit.DAYS).toString());
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isCreated());
        }

        @Test
        void 깨진_JSON은_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ broken"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("보관 분반 - 열람 유지, 쓰기는 409")
    class ArchivedCohort {

        @Test
        void 보관되면_조회는_200_쓰기는_409_해제하면_다시_가능() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long assignmentId = createAssignment(id, 1, "1차시 과제");
            archiveCohort(id);

            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("s1")))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(2, "새 과제"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            mockMvc.perform(put("/api/cohorts/{id}/assignments/{aid}", id, assignmentId)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "수정"))))
                    .andExpect(status().isConflict());
            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.admin()))
                    .andExpect(status().isConflict());

            restoreCohort(id);
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(2, "복구 후 과제"))))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("동작 확인")
    class Behavior {

        @Test
        void 등록_응답에_Location_헤더와_본문_id가_있다() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/assignments", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(assignmentBody(1, "1차시 과제"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/assignments/")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.sessionNo").value(1))
                    .andExpect(jsonPath("$.title").value("1차시 과제"));
        }

        @Test
        void 수정하면_재조회에_반영된다() throws Exception {
            long id = createCohort("C언어", "op1");
            long assignmentId = createAssignment(id, 1, "원래 제목");

            Map<String, Object> body = assignmentBody(3, "바뀐 제목");
            mockMvc.perform(put("/api/cohorts/{id}/assignments/{aid}", id, assignmentId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionNo").value(3))
                    .andExpect(jsonPath("$.title").value("바뀐 제목"));
        }

        @Test
        void 목록은_차시_오름차순_같은_차시는_등록순_차시_없음은_마지막() throws Exception {
            long id = createCohort("C언어", "op1");
            createAssignment(id, 2, "과제B");
            createAssignment(id, null, "과제D");
            createAssignment(id, 1, "과제A");
            createAssignment(id, 2, "과제C");

            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].title").value("과제A"))
                    .andExpect(jsonPath("$[1].title").value("과제B"))
                    .andExpect(jsonPath("$[2].title").value("과제C"))
                    .andExpect(jsonPath("$[3].title").value("과제D"))
                    .andExpect(jsonPath("$[3].sessionNo", nullValue()));
        }

        @Test
        void 과제가_없으면_빈_배열() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 삭제하면_목록에서_사라진다() throws Exception {
            long id = createCohort("C언어", "op1");
            long assignmentId = createAssignment(id, 1, "지울 과제");

            mockMvc.perform(delete("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("op1")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/cohorts/{id}/assignments", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}", id, assignmentId).session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }
    }
}
