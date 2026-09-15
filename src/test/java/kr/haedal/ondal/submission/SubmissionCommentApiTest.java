package kr.haedal.ondal.submission;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 제출 코멘트 API (SubmissionCommentController #45~#46) + 제출 응답의 comment / hasComment */
class SubmissionCommentApiTest extends ApiTestSupport {

    private static final Instant FUTURE_DUE = Instant.now().plus(7, ChronoUnit.DAYS);

    // ---- 슬라이스 고유 픽스처 ------------------------------------------------------------

    /** V7: 문제를 만들어 분반에 배정한다 (공용 픽스처) */
    private long createAssignment(long cohortId) throws Exception {
        return createAssignmentOf(cohortId, "과제", null, FUTURE_DUE);
    }

    private long submitCode(long cohortId, long assignmentId, MockHttpSession session) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "CODE");
        body.put("codeText", "print(1)");
        body.put("language", "Python 3");
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json", json(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/cohorts/{id}/assignments/{aid}/submissions", cohortId, assignmentId)
                        .file(request)
                        .session(session))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private String commentPath(long cohortId, long assignmentId, long submissionId) {
        return "/api/cohorts/" + cohortId + "/assignments/" + assignmentId + "/submissions/" + submissionId + "/comment";
    }

    @Nested
    @DisplayName("권한과 동작")
    class Behavior {

        @Test
        void 학생은_403_운영진은_남기고_학생이_읽고_덮어쓰고_지운다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long aid = createAssignment(id);
            long sid = submitCode(id, aid, login.member("s1"));

            // 제출 직후 - 코멘트 없음
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, sid).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comment").isEmpty());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].hasComment").value(false));

            // 학생(본인·타인) → 403
            for (MockHttpSession denied : new MockHttpSession[]{login.member("s1"), login.member("s2")}) {
                mockMvc.perform(put(commentPath(id, aid, sid))
                                .session(denied)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("content", "학생 코멘트 시도"))))
                        .andExpect(status().isForbidden());
            }

            // 운영진 남기기 → 응답에 comment(작성 운영진 직책)
            mockMvc.perform(put(commentPath(id, aid, sid))
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "  변수명을 더 의미 있게  "))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comment.content").value("변수명을 더 의미 있게"))
                    .andExpect(jsonPath("$.comment.author.title").value("교육운영진"))
                    .andExpect(jsonPath("$.comment.author.loginId").doesNotExist())
                    .andExpect(jsonPath("$.comment.commentedAt").isNotEmpty());

            // 학생 본인이 읽는다 - 상세·이력
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, sid).session(login.member("s1")))
                    .andExpect(jsonPath("$.comment.content").value("변수명을 더 의미 있게"));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].hasComment").value(true));
            // 다른 학생은 여전히 404(타인 제출물 비노출)
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, sid).session(login.member("s2")))
                    .andExpect(status().isNotFound());

            // 현황판(이름순: s1, s2) - s1 최신 제출에 코멘트 있음 → true, 미제출 s2 → false
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid).session(login.member("op1")))
                    .andExpect(jsonPath("$[0].latestCommented").value(true))
                    .andExpect(jsonPath("$[1].latestCommented").value(false));
            // s1 이 다시 제출하면 새 제출(최신)에는 코멘트가 없다 - 현황판 false, 이력은 최신순으로 [없음, 있음]
            submitCode(id, aid, login.member("s1"));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/status-board", id, aid).session(login.member("op1")))
                    .andExpect(jsonPath("$[0].latestCommented").value(false));
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/my", id, aid).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].hasComment").value(false))
                    .andExpect(jsonPath("$[1].hasComment").value(true));

            // 관리자가 덮어쓰기 → 작성자 해구르르
            mockMvc.perform(put(commentPath(id, aid, sid))
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "관리자 덮어쓰기"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comment.content").value("관리자 덮어쓰기"))
                    .andExpect(jsonPath("$.comment.author.title").value("해구르르"));

            // 지우기(멱등) → comment null
            mockMvc.perform(delete(commentPath(id, aid, sid)).session(login.member("op1"))).andExpect(status().isNoContent());
            mockMvc.perform(delete(commentPath(id, aid, sid)).session(login.member("op1"))).andExpect(status().isNoContent());
            mockMvc.perform(get("/api/cohorts/{id}/assignments/{aid}/submissions/{sid}", id, aid, sid).session(login.member("s1")))
                    .andExpect(jsonPath("$.comment").isEmpty());
        }

        @Test
        void 빈_내용_400_없는_제출_404_보관_409() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long aid = createAssignment(id);
            long sid = submitCode(id, aid, login.member("s1"));

            mockMvc.perform(put(commentPath(id, aid, sid))
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "   "))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(put(commentPath(id, aid, 99999))
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "없는 제출"))))
                    .andExpect(status().isNotFound());

            archiveCohort(id);
            mockMvc.perform(put(commentPath(id, aid, sid))
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "보관 중"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            mockMvc.perform(delete(commentPath(id, aid, sid)).session(login.member("op1")))
                    .andExpect(status().isConflict());
            restoreCohort(id);
            mockMvc.perform(put(commentPath(id, aid, sid))
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "해제 후"))))
                    .andExpect(status().isOk());
        }
    }
}
