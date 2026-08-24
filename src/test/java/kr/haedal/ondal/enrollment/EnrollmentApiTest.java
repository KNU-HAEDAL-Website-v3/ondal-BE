package kr.haedal.ondal.enrollment;

import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.service.EnrollmentService;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import kr.haedal.ondal.common.error.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 소속 API (EnrollmentController #1, #8~#10) */
class EnrollmentApiTest extends ApiTestSupport {

    @Autowired EnrollmentService enrollmentService;

    @Nested
    @DisplayName("GET /api/me/cohorts - 내 분반")
    class MyCohorts {

        @Test
        void 미소속이면_빈_배열() throws Exception {
            mockMvc.perform(get("/api/me/cohorts").session(login.member("lonely")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 다른_사람의_소속은_섞여_오지_않는다() throws Exception {
            long a = createCohort("A반", "op1");
            long b = createCohort("B반", "op2");
            enrollStudent(a, "s1");
            enrollStudent(b, "s2");
            mockMvc.perform(get("/api/me/cohorts").session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(a));
            mockMvc.perform(get("/api/me/cohorts").session(login.member("nobody")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 소속_분반이_myRole_운영진과_함께_오고_보관_분반은_뒤에_온다() throws Exception {
            long past = createCohort("2026-1 파이썬", "op1");
            long current = createCohort("2026-2 C언어", "op1");
            enrollStudent(past, "s1");
            enrollStudent(current, "s1");
            archiveCohort(past);

            mockMvc.perform(get("/api/me/cohorts").session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(current))
                    .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$[0].myRole").value("STUDENT"))
                    .andExpect(jsonPath("$[0].operators[0].name").value("op1"))
                    .andExpect(jsonPath("$[1].id").value(past))
                    .andExpect(jsonPath("$[1].status").value("ARCHIVED"));
        }

        @Test
        void 운영진에게는_canManage_true와_수강생수() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(get("/api/me/cohorts").session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].myRole").value("OPERATOR"))
                    .andExpect(jsonPath("$[0].canManage").value(true))
                    .andExpect(jsonPath("$[0].studentCount").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/cohorts/{cohortId}/members - 명부")
    class Members {

        @Test
        void 수강생은_403_운영진은_200_운영진이_먼저() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");

            mockMvc.perform(get("/api/cohorts/{id}/members", id).session(login.member("s1")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/cohorts/{id}/members", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].role").value("OPERATOR"))
                    .andExpect(jsonPath("$[0].title").value("교육운영진"))
                    .andExpect(jsonPath("$[0].user.loginId").value("op1"))
                    .andExpect(jsonPath("$[1].role").value("STUDENT"))
                    .andExpect(jsonPath("$[1].title").value("일반 수강생"));
        }

        @Test
        void 관리자는_비소속이어도_200() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/members", id).session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("PUT /api/cohorts/{cohortId}/operators/{loginId} - 운영진 지정")
    class AssignOperator {

        @Test
        void 운영진은_403() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", id, "op2").session(login.member("op1")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 관리자_지정은_멱등이고_아직_로그인한_적_없는_loginId도_된다() throws Exception {
            long id = createCohort("C언어");
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", id, "newbie").session(login.admin()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.user.loginId").value("newbie"))
                        .andExpect(jsonPath("$.role").value("OPERATOR"));
            }
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(1);
        }

        @Test
        void 수강생이던_사람은_운영진으로_승격된다() throws Exception {
            long id = createCohort("C언어");
            enrollStudent(id, "s1");
            mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", id, "s1").session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("OPERATOR"));
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id))
                    .singleElement().extracting(Enrollment::getRole).isEqualTo(EnrollmentRole.OPERATOR);
        }

        @Test
        void 보관된_분반은_409_해제하면_다시_200() throws Exception {
            long id = createCohort("C언어");
            archiveCohort(id);
            mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", id, "op1").session(login.admin()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));

            restoreCohort(id);
            mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", id, "op1").session(login.admin()))
                    .andExpect(status().isOk());
        }

        @Test
        void 없는_분반이면_지정_해제_명부_모두_404() throws Exception {
            mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", 999_999, "op1").session(login.admin()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mockMvc.perform(delete("/api/cohorts/{id}/operators/{loginId}", 999_999, "op1").session(login.admin()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/cohorts/{id}/members", 999_999).session(login.admin()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void loginId가_50자를_넘으면_400() throws Exception {
            long id = createCohort("C언어");
            mockMvc.perform(put("/api/cohorts/{id}/operators/{loginId}", id, "x".repeat(51)).session(login.admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void assign은_다른_역할로_이미_소속이면_409이고_역할을_바꾸지_않는다() throws Exception {
            // 이 분기는 다음 슬라이스(수강생 명단 배정)에서 API 로 도달한다 - 지금은 서비스 레벨로 고정해 둔다
            long id = createCohort("C언어", "op1");
            assertThatThrownBy(() -> enrollmentService.assign(id, List.of("op1"), EnrollmentRole.STUDENT))
                    .isInstanceOf(ConflictException.class);
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id))
                    .singleElement().extracting(Enrollment::getRole).isEqualTo(EnrollmentRole.OPERATOR);
        }
    }

    @Nested
    @DisplayName("DELETE /api/cohorts/{cohortId}/operators/{loginId} - 운영진 해제")
    class RemoveOperator {

        @Test
        void 운영진_해제는_204이고_소속이_사라진다() throws Exception {
            long id = createCohort("C언어", "op1", "op2");
            mockMvc.perform(delete("/api/cohorts/{id}/operators/{loginId}", id, "op1").session(login.admin()))
                    .andExpect(status().isNoContent());
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(1);
        }

        @Test
        void 수강생_loginId로_지우려_하면_404이고_소속은_유지된다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(delete("/api/cohorts/{id}/operators/{loginId}", id, "s1").session(login.admin()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(2);
        }

        @Test
        void 모르는_loginId면_404() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(delete("/api/cohorts/{id}/operators/{loginId}", id, "ghost").session(login.admin()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 보관된_분반의_운영진_해제는_409() throws Exception {
            long id = createCohort("C언어", "op1");
            archiveCohort(id);
            mockMvc.perform(delete("/api/cohorts/{id}/operators/{loginId}", id, "op1").session(login.admin()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(1);
        }

        @Test
        void 마지막_운영진도_해제할_수_있다_관리자는_항상_운영자_이상() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(delete("/api/cohorts/{id}/operators/{loginId}", id, "op1").session(login.admin()))
                    .andExpect(status().isNoContent());
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST /api/cohorts/{cohortId}/students - 수강생 일괄 배정 (#11)")
    class AssignStudents {

        @Test
        void 미로그인이면_401() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 같은_반_수강생이면_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s2")))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void 다른_반_운영진이면_403() throws Exception {
            createCohort("다른반", "otherOp");
            long mine = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", mine)
                            .session(login.member("otherOp"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 자기_반_운영진은_배정하고_갱신된_명부를_받는다() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1", "s2", "s1"))))) // 중복은 한 번만
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))                       // op1 + s1 + s2
                    .andExpect(jsonPath("$[0].role").value("OPERATOR"))          // 명부 정렬: 운영진 먼저
                    .andExpect(jsonPath("$[1].role").value("STUDENT"))
                    .andExpect(jsonPath("$[1].title").value("일반 수강생"))
                    .andExpect(jsonPath("$[1].user.loginId").value("s1"));       // 명부는 운영진 이상만 보므로 loginId 포함
        }

        @Test
        void 관리자도_배정할_수_있다() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void 이미_수강생인_사람을_다시_배정해도_그대로_200_멱등() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void 운영진인_loginId가_섞여_있으면_409_역할을_바꾸지_않는다() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("op1")))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }

        @Test
        void 보관된_분반이면_409() throws Exception {
            long id = createCohort("C언어", "op1");
            archiveCohort(id);
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
        }

        @Test
        void 빈_목록이면_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void loginId가_50자를_넘으면_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/students", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("x".repeat(51))))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 없는_분반이면_404() throws Exception {
            mockMvc.perform(post("/api/cohorts/{id}/students", 9999)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/cohorts/{cohortId}/students/{loginId} - 수강생 제외 (#12)")
    class RemoveStudent {

        @Test
        void 자기_반_운영진은_204_명부에서_사라진다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(delete("/api/cohorts/{id}/students/{loginId}", id, "s1").session(login.member("op1")))
                    .andExpect(status().isNoContent());
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(1); // op1만 남는다
        }

        @Test
        void 같은_반_수강생이면_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(delete("/api/cohorts/{id}/students/{loginId}", id, "s1").session(login.member("s1")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 운영진_loginId를_지정하면_404_소속_유지() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(delete("/api/cohorts/{id}/students/{loginId}", id, "op1").session(login.admin()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(1);
        }

        @Test
        void 미소속이거나_모르는_loginId면_404() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(delete("/api/cohorts/{id}/students/{loginId}", id, "ghost").session(login.admin()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 보관된_분반이면_409_소속_유지() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            archiveCohort(id);
            mockMvc.perform(delete("/api/cohorts/{id}/students/{loginId}", id, "s1").session(login.admin()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            assertThat(enrollmentRepository.findAllByCohortIdWithUser(id)).hasSize(2);
        }
    }
}

