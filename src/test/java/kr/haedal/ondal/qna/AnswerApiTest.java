package kr.haedal.ondal.qna;

import kr.haedal.ondal.qna.repository.AnswerRepository;
import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Q&A 답변 API (AnswerController #41~#44) + 질문 응답의 answerCount·질문 삭제 연쇄 */
class AnswerApiTest extends ApiTestSupport {

    @Autowired
    private AnswerRepository answerRepository;

    // ---- 슬라이스 고유 픽스처 ------------------------------------------------------------

    private long createQuestion(long cohortId, MockHttpSession author, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/questions", cohortId)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", title, "content", title + " 내용"))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    private long createAnswer(long cohortId, long questionId, MockHttpSession author, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/questions/{qid}/answers", cohortId, questionId)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", content))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Nested
    @DisplayName("인증과 권한")
    class Authorization {

        @Test
        void 미로그인_401_비소속_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "질문");

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}/answers", id, questionId))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}/answers", id, questionId).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/cohorts/{id}/questions/{qid}/answers", id, questionId)
                            .session(login.member("outsider"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "남의 반 답변"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 소속_누구나_등록_목록은_오래된순_질문에_answerCount() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long questionId = createQuestion(id, login.member("s1"), "질문");

            mockMvc.perform(post("/api/cohorts/{id}/questions/{qid}/answers", id, questionId)
                            .session(login.member("s2"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "첫 답변"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/answers/")))
                    .andExpect(jsonPath("$.author.title").value("일반 수강생"))
                    .andExpect(jsonPath("$.author.loginId").doesNotExist())
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andExpect(jsonPath("$.canDelete").value(true));
            createAnswer(id, questionId, login.member("op1"), "둘째 답변");

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}/answers", id, questionId).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].content").value("첫 답변"))
                    .andExpect(jsonPath("$[0].canEdit").value(false))    // 질문자는 남의 답변 수정 불가
                    .andExpect(jsonPath("$[0].canDelete").value(false))
                    .andExpect(jsonPath("$[1].author.title").value("교육운영진"));
            // 질문 응답에 답변 수
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(jsonPath("$.answerCount").value(2));
            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("s1")))
                    .andExpect(jsonPath("$[0].answerCount").value(2));
        }

        @Test
        void 수정은_작성자만_삭제는_작성자_또는_운영진() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long questionId = createQuestion(id, login.member("s1"), "질문");
            long byS2 = createAnswer(id, questionId, login.member("s2"), "s2 답변");
            long byS2Again = createAnswer(id, questionId, login.member("s2"), "s2 둘째 답변");
            long byS2Third = createAnswer(id, questionId, login.member("s2"), "s2 셋째 답변");

            for (MockHttpSession other : new MockHttpSession[]{login.member("s1"), login.member("op1"), login.admin()}) {
                mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, byS2)
                                .session(other)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("content", "남의 답변 수정 시도"))))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, byS2)
                            .session(login.member("s2"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "본인 수정"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("본인 수정"));

            // 삭제: 다른 수강생(질문자 포함) 403 / 본인 204 / 운영진 204 / 관리자 204
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, byS2).session(login.member("s1")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, byS2).session(login.member("s2")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, byS2Again).session(login.member("op1")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, byS2Third).session(login.admin()))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}/answers", id, questionId).session(login.member("s1")))
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("스코프·검증·연쇄")
    class ScopeAndCascade {

        @Test
        void 다른_질문의_답변은_404_빈_내용은_400() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long q1 = createQuestion(id, login.member("s1"), "질문 1");
            long q2 = createQuestion(id, login.member("s1"), "질문 2");
            long answerOfQ1 = createAnswer(id, q1, login.member("s1"), "q1 답변");

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}/answers", id, 99999).session(login.member("s1")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, q2, answerOfQ1)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "교차 수정"))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, q2, answerOfQ1).session(login.member("s1")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/cohorts/{id}/questions/{qid}/answers", id, q1)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", " "))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 질문_삭제_시_답변_연쇄_삭제() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "질문");
            createAnswer(id, questionId, login.member("op1"), "답변 1");
            createAnswer(id, questionId, login.member("s1"), "답변 2");
            assertThat(answerRepository.count()).isEqualTo(2);

            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(status().isNoContent());
            assertThat(answerRepository.count()).isZero();
        }

        @Test
        void 보관_분반은_열람_유지_쓰기_409() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "질문");
            long answerId = createAnswer(id, questionId, login.member("op1"), "답변");
            archiveCohort(id);

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}/answers", id, questionId).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].canDelete").value(false));
            mockMvc.perform(post("/api/cohorts/{id}/questions/{qid}/answers", id, questionId)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("content", "보관 중 답변"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, answerId).session(login.member("op1")))
                    .andExpect(status().isConflict());
            restoreCohort(id);
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}/answers/{aid}", id, questionId, answerId).session(login.member("op1")))
                    .andExpect(status().isNoContent());
        }
    }
}
