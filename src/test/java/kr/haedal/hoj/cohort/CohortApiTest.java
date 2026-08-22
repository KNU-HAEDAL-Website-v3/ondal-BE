package kr.haedal.hoj.cohort;

import kr.haedal.hoj.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 분반 API (CohortController #2~#7). "역할 × 엔드포인트 → 상태코드" 를 검증한다.
 * 이후 슬라이스의 테스트는 이 파일의 구조를 그대로 복제한다: @Nested 로 엔드포인트별 묶음, 메서드명은 한국어 시나리오.
 */
class CohortApiTest extends ApiTestSupport {

    @Nested
    @DisplayName("POST /api/cohorts - 분반 생성")
    class Create {

        @Test
        void 미로그인이면_401() throws Exception {
            mockMvc.perform(post("/api/cohorts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "2026-2 C언어"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        @Test
        void 일반부원이면_403() throws Exception {
            mockMvc.perform(post("/api/cohorts")
                            .session(login.member("member1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "2026-2 C언어"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void 관리자는_201과_Location_그리고_운영진이_채워진_응답() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/cohorts")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "name", "2026-2 C언어",
                                    "description", "C 트랙",
                                    "operatorLoginIds", List.of("op1", "op2", "op1"))))) // 중복은 한 번만
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", startsWith("/api/cohorts/")))
                    .andExpect(jsonPath("$.name").value("2026-2 C언어"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.operators", hasSize(2)))
                    .andExpect(jsonPath("$.operators[*].name").value(containsInAnyOrder("op1", "op2")))   // 스텁 로그인은 name == loginId
                    .andExpect(jsonPath("$.operators[0].loginId").doesNotExist())                     // 학생에게도 내려가는 응답 - loginId·globalRole 없음
                    .andExpect(jsonPath("$.operators[0].globalRole").doesNotExist())
                    .andExpect(jsonPath("$.studentCount").value(0))
                    .andExpect(jsonPath("$.myRole").value(nullValue()))   // 관리자는 비소속
                    .andExpect(jsonPath("$.canManage").value(true))
                    .andReturn();
            long id = readJson(result).get("id").asLong();
            assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/cohorts/" + id);
        }

        @Test
        void 운영진_loginId가_비어_있거나_50자를_넘으면_400() throws Exception {
            mockMvc.perform(post("/api/cohorts")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "C언어", "operatorLoginIds", List.of("op1", " ")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(post("/api/cohorts")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "C언어", "operatorLoginIds", List.of("x".repeat(51))))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 깨진_JSON_본문은_400() throws Exception {
            mockMvc.perform(post("/api/cohorts")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{bad json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 이름이_비면_400() throws Exception {
            mockMvc.perform(post("/api/cohorts")
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "  "))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("GET /api/cohorts/{cohortId} - 분반 상세 (소속자)")
    class GetOne {

        @Test
        void 비소속_부원은_403() throws Exception {
            long id = createCohort("C언어");
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 수강생은_200_myRole_STUDENT_canManage_false_수강생수는_숨김() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myRole").value("STUDENT"))
                    .andExpect(jsonPath("$.canManage").value(false))
                    .andExpect(jsonPath("$.studentCount").value(nullValue()))
                    .andExpect(jsonPath("$.operators", hasSize(1)))       // 운영진 이름은 학생에게도 공개 - 이름만
                    .andExpect(jsonPath("$.operators[0].name").value("op1"))
                    .andExpect(jsonPath("$.operators[0].title").value("교육운영진"))
                    .andExpect(jsonPath("$.operators[0].loginId").doesNotExist())
                    .andExpect(jsonPath("$.myTitle").value("일반 수강생"));
        }

        @Test
        void 운영진은_200_canManage_true_수강생수_보임() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myRole").value("OPERATOR"))
                    .andExpect(jsonPath("$.myTitle").value("교육운영진"))
                    .andExpect(jsonPath("$.canManage").value(true))
                    .andExpect(jsonPath("$.studentCount").value(2));
        }

        @Test
        void 비소속_관리자도_200_myRole_null_canManage_true() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.myRole").value(nullValue()))
                    .andExpect(jsonPath("$.myTitle").value("해구르르"))
                    .andExpect(jsonPath("$.canManage").value(true))
                    .andExpect(jsonPath("$.studentCount").value(0));
        }

        @Test
        void 임원이_운영진으로_소속돼_있으면_학생_화면에도_해구르르로_보인다() throws Exception {
            long id = createCohort("C언어", "admin");   // 시더/LoginHelper 의 admin 계정을 운영진으로 지정
            enrollStudent(id, "s1");
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operators[0].name").value("관리자"))
                    .andExpect(jsonPath("$.operators[0].title").value("해구르르"))
                    .andExpect(jsonPath("$.operators[0].globalRole").doesNotExist());
        }

        @Test
        void 숫자가_아닌_id는_400_관리자_세션이어도_400() throws Exception {
            mockMvc.perform(get("/api/cohorts/abc").session(login.member("s1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(get("/api/cohorts/abc").session(login.admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            // @AdminOnly 경로는 인터셉터가 아니라 @PathVariable 바인딩에서 걸린다 - 역시 400
            mockMvc.perform(post("/api/cohorts/abc/archive").session(login.admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 다른_분반_소속자는_이_분반에_403_교차_분반_차단() throws Exception {
            long mine = createCohort("내 반", "op1");
            long other = createCohort("남의 반", "op2");
            enrollStudent(mine, "s1");
            // s1(내 반 수강생), op1(내 반 운영진) 모두 남의 반은 403
            mockMvc.perform(get("/api/cohorts/{id}", other).session(login.member("s1")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/cohorts/{id}", other).session(login.member("op1")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/cohorts/{id}/members", other).session(login.member("op1")))
                    .andExpect(status().isForbidden());
            // 자기 반은 그대로 200
            mockMvc.perform(get("/api/cohorts/{id}", mine).session(login.member("s1")))
                    .andExpect(status().isOk());
        }

        @Test
        void 없는_id는_관리자에게_404_일반부원에게는_403() throws Exception {
            mockMvc.perform(get("/api/cohorts/{id}", 999_999).session(login.admin()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mockMvc.perform(get("/api/cohorts/{id}", 999_999).session(login.member("s1")))
                    .andExpect(status().isForbidden()); // 존재 여부를 노출하지 않는다
        }
    }

    @Nested
    @DisplayName("GET /api/cohorts - 관리자 목록")
    class ListAll {

        @Test
        void 운영진이어도_전체_목록은_403() throws Exception {
            createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts").session(login.member("op1")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 기본은_ACTIVE만_보관은_status_파라미터로() throws Exception {
            long a = createCohort("진행중 반");
            long b = createCohort("보관될 반");
            archiveCohort(b);

            mockMvc.perform(get("/api/cohorts").session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(a));

            mockMvc.perform(get("/api/cohorts").param("status", "ARCHIVED").session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(b))
                    .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
        }

        @Test
        void 잘못된_status_값은_400() throws Exception {
            mockMvc.perform(get("/api/cohorts").param("status", "FOO").session(login.admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Nested
    @DisplayName("PUT /api/cohorts/{cohortId} - 수정")
    class Update {

        @Test
        void 운영진은_403_관리자는_200() throws Exception {
            long id = createCohort("C언어", "op1");
            String body = json(Map.of("name", "2026-2 C언어 (수정)", "description", "바뀜"));

            mockMvc.perform(put("/api/cohorts/{id}", id).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());

            mockMvc.perform(put("/api/cohorts/{id}", id).session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("2026-2 C언어 (수정)"))
                    .andExpect(jsonPath("$.description").value("바뀜"));

            // 별도 요청으로 재조회 - 더티체킹이 실제로 flush 됐는지
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.admin()))
                    .andExpect(jsonPath("$.name").value("2026-2 C언어 (수정)"))
                    .andExpect(jsonPath("$.description").value("바뀜"));
        }

        @Test
        void 보관된_분반은_관리자도_409_COHORT_ARCHIVED() throws Exception {
            long id = createCohort("C언어");
            archiveCohort(id);
            mockMvc.perform(put("/api/cohorts/{id}", id).session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("name", "바꾸기"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
        }
    }

    @Nested
    @DisplayName("POST /api/cohorts/{cohortId}/archive · /restore - 보관/해제")
    class ArchiveRestore {

        @Test
        void 보관과_해제는_멱등이고_상태가_바뀐다() throws Exception {
            long id = createCohort("C언어");

            mockMvc.perform(post("/api/cohorts/{id}/archive", id).session(login.admin()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
            mockMvc.perform(post("/api/cohorts/{id}/archive", id).session(login.admin()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));

            mockMvc.perform(post("/api/cohorts/{id}/restore", id).session(login.admin()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
            mockMvc.perform(post("/api/cohorts/{id}/restore", id).session(login.admin()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void 보관되면_운영진은_여전히_열람되지만_canManage는_false() throws Exception {
            long id = createCohort("C언어", "op1");
            archiveCohort(id);
            mockMvc.perform(get("/api/cohorts/{id}", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.myRole").value("OPERATOR"))
                    .andExpect(jsonPath("$.canManage").value(false));
        }

        @Test
        void 운영진은_보관_403() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/archive", id).session(login.member("op1")))
                    .andExpect(status().isForbidden());
        }
    }
}
