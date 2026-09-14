package kr.haedal.ondal.notice;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공지사항 API (NoticeController #28~#33) */
class NoticeApiTest extends ApiTestSupport {

    // ---- 슬라이스 고유 픽스처 (support/는 PM 파일 - 여기 private 헬퍼로) ----------------------

    private Map<String, Object> noticeBody(String title, boolean pinned) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", title + " 내용");
        body.put("pinned", pinned);
        return body;
    }

    /** 전체 공지 - 관리자 세션 */
    private long createGlobal(String title, boolean pinned) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notices")
                        .session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noticeBody(title, pinned))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 분반 공지 - 세션 주인이 작성자 (운영진 이상) */
    private long createForCohort(long cohortId, MockHttpSession author, String title, boolean pinned) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/notices", cohortId)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(noticeBody(title, pinned))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Nested
    @DisplayName("인증과 권한 - 역할 x 엔드포인트")
    class Authorization {

        @Test
        void 미로그인이면_401() throws Exception {
            mockMvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
        }

        @Test
        void 전체_공지_등록은_관리자만_부원은_403() throws Exception {
            mockMvc.perform(post("/api/notices")
                            .session(login.member("m1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("부원 시도", false))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            MvcResult result = mockMvc.perform(post("/api/notices")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("운영 안내", true))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/notices/")))
                    .andExpect(jsonPath("$.cohort").isEmpty())
                    .andExpect(jsonPath("$.pinned").value(true))
                    .andExpect(jsonPath("$.author.title").value("해구르르"))
                    .andExpect(jsonPath("$.author.loginId").doesNotExist())
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andExpect(jsonPath("$.canDelete").value(true))
                    .andReturn();
            long id = readJson(result).get("id").asLong();

            // 전체 공지는 비소속 부원도 본다 - 단 수정 권한은 없다
            mockMvc.perform(get("/api/notices/{id}", id).session(login.member("m1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("운영 안내"))
                    .andExpect(jsonPath("$.canEdit").value(false));
        }

        @Test
        void 분반_공지_등록은_운영진_이상_수강생_비소속은_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");

            for (MockHttpSession denied : new MockHttpSession[]{login.member("s1"), login.member("outsider")}) {
                mockMvc.perform(post("/api/cohorts/{id}/notices", id)
                                .session(denied)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(noticeBody("권한 없는 공지", false))))
                        .andExpect(status().isForbidden());
            }

            mockMvc.perform(post("/api/cohorts/{id}/notices", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("첫 모임 안내", false))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cohort.id").value(id))
                    .andExpect(jsonPath("$.cohort.name").value("C언어"))
                    .andExpect(jsonPath("$.author.title").value("교육운영진"))
                    .andExpect(jsonPath("$.canEdit").value(true));

            // 비소속 관리자도 등록 가능
            mockMvc.perform(post("/api/cohorts/{id}/notices", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("관리자 공지", false))))
                    .andExpect(status().isCreated());
        }

        @Test
        void 목록은_전체_공지와_소속_분반_공지만_필독_먼저() throws Exception {
            long a = createCohort("A반", "opA");
            long b = createCohort("B반", "opB");
            enrollStudent(a, "s1");
            createGlobal("전체 공지", false);
            createForCohort(a, login.member("opA"), "A반 필독 공지", true);
            createForCohort(b, login.member("opB"), "B반 공지", false);

            // A반 수강생: 전체 + A반 (필독이 먼저)
            mockMvc.perform(get("/api/notices").session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].title").value("A반 필독 공지"))
                    .andExpect(jsonPath("$[0].canEdit").value(false))
                    .andExpect(jsonPath("$[1].title").value("전체 공지"));
            // 비소속 부원: 전체 공지만
            mockMvc.perform(get("/api/notices").session(login.member("outsider")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].title").value("전체 공지"));
            // 관리자: 전부, 모두 관리 가능
            mockMvc.perform(get("/api/notices").session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].canEdit").value(true))
                    .andExpect(jsonPath("$[2].canEdit").value(true));
            // A반 운영진: A반 공지는 관리 가능, 전체 공지는 불가
            mockMvc.perform(get("/api/notices").session(login.member("opA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].canEdit").value(true))
                    .andExpect(jsonPath("$[1].canEdit").value(false));
        }

        @Test
        void 분반_공지_상세는_비소속_403_소속_200() throws Exception {
            long a = createCohort("A반", "opA");
            enrollStudent(a, "s1");
            long noticeId = createForCohort(a, login.member("opA"), "A반 공지", false);

            mockMvc.perform(get("/api/notices/{id}", noticeId).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/notices/{id}", noticeId).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cohort.name").value("A반"));
            mockMvc.perform(get("/api/notices/{id}", noticeId).session(login.admin()))
                    .andExpect(status().isOk());
        }

        @Test
        void 수정_삭제는_관리_권한_수강생_403_운영진_관리자_통과() throws Exception {
            long a = createCohort("A반", "opA");
            enrollStudent(a, "s1");
            long cohortNotice = createForCohort(a, login.member("opA"), "A반 공지", false);
            long globalNotice = createGlobal("전체 공지", false);

            // 수강생·비소속은 403
            for (MockHttpSession denied : new MockHttpSession[]{login.member("s1"), login.member("outsider")}) {
                mockMvc.perform(put("/api/notices/{id}", cohortNotice)
                                .session(denied)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(noticeBody("수정 시도", true))))
                        .andExpect(status().isForbidden());
                mockMvc.perform(delete("/api/notices/{id}", cohortNotice).session(denied))
                        .andExpect(status().isForbidden());
            }
            // 운영진은 자기 반 공지 수정 200 - 전체 공지는 403
            mockMvc.perform(put("/api/notices/{id}", cohortNotice)
                            .session(login.member("opA"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("A반 공지 (수정)", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("A반 공지 (수정)"))
                    .andExpect(jsonPath("$.pinned").value(true));
            mockMvc.perform(put("/api/notices/{id}", globalNotice)
                            .session(login.member("opA"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("전체 공지 수정 시도", false))))
                    .andExpect(status().isForbidden());
            // 관리자는 둘 다
            mockMvc.perform(put("/api/notices/{id}", globalNotice)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("전체 공지 (수정)", false))))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/notices/{id}", cohortNotice).session(login.admin()))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/notices/{id}", cohortNotice).session(login.admin()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("입력 검증과 404")
    class Validation {

        @Test
        void 제목_공백이나_내용_초과는_400() throws Exception {
            mockMvc.perform(post("/api/notices")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", " ", "content", "내용"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(post("/api/notices")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "제목", "content", "x".repeat(10001)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void pinned_생략은_false() throws Exception {
            mockMvc.perform(post("/api/notices")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "제목", "content", "내용"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pinned").value(false));
        }

        @Test
        void 없는_공지는_404_없는_분반은_관리자_404_부원_403() throws Exception {
            mockMvc.perform(get("/api/notices/{id}", 99999).session(login.admin()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/cohorts/{id}/notices", 99999)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("없는 분반", false))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/cohorts/{id}/notices", 99999)
                            .session(login.member("m1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("없는 분반", false))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("보관 분반")
    class Archived {

        @Test
        void 보관_분반은_열람_유지_쓰기_409_해제_후_복구() throws Exception {
            long a = createCohort("A반", "opA");
            enrollStudent(a, "s1");
            long noticeId = createForCohort(a, login.member("opA"), "A반 공지", false);
            archiveCohort(a);

            mockMvc.perform(get("/api/notices").session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(get("/api/notices/{id}", noticeId).session(login.member("opA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canEdit").value(false));

            mockMvc.perform(post("/api/cohorts/{id}/notices", a)
                            .session(login.member("opA"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("보관 중 등록", false))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            mockMvc.perform(put("/api/notices/{id}", noticeId)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("보관 중 수정", false))))
                    .andExpect(status().isConflict());
            mockMvc.perform(delete("/api/notices/{id}", noticeId).session(login.admin()))
                    .andExpect(status().isConflict());

            restoreCohort(a);
            mockMvc.perform(put("/api/notices/{id}", noticeId)
                            .session(login.member("opA"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(noticeBody("해제 후 수정", false))))
                    .andExpect(status().isOk());
        }
    }
}
