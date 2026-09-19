package kr.haedal.ondal.user;

import kr.haedal.ondal.support.ApiTestSupport;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 부원 목록·승인 API (UserController) + 승인 대기 게이트 (AuthorizationInterceptor) - docs 결정 10 */
class UserApiTest extends ApiTestSupport {

    private User pending(String loginId) {
        return userRepository.save(User.pending(loginId, loginId + " 이름"));
    }

    @Nested
    @DisplayName("GET /api/users - 부원 목록")
    class Directory {

        @Test
        void 수강생은_403() throws Exception {
            long a = createCohort("A반", "op1");
            mockMvc.perform(post("/api/cohorts/" + a + "/students").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/users").session(login.member("s1")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/users").session(login.member("nobody")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 운영진은_전체_부원과_소속_요약을_본다_대기가_먼저() throws Exception {
            long a = createCohort("A반", "op1");
            mockMvc.perform(post("/api/cohorts/" + a + "/students").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isOk());
            pending("newbie");

            mockMvc.perform(get("/api/users").session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))   // admin(createCohort 가 만든 부트스트랩) · op1 · s1 · newbie
                    .andExpect(jsonPath("$[0].loginId").value("newbie"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"))
                    .andExpect(jsonPath("$[0].enrollments", hasSize(0)))
                    .andExpect(jsonPath("$[?(@.loginId == 's1')].enrollments[0].cohortName").value("A반"))
                    .andExpect(jsonPath("$[?(@.loginId == 's1')].enrollments[0].role").value("STUDENT"))
                    .andExpect(jsonPath("$[?(@.loginId == 'op1')].enrollments[0].role").value("OPERATOR"))
                    .andExpect(jsonPath("$[?(@.loginId == 'admin')].globalRole").value("ADMIN"));
        }

        @Test
        void status_로_거른다() throws Exception {
            pending("p1");
            pending("p2");
            mockMvc.perform(get("/api/users").param("status", "PENDING").session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))));
            mockMvc.perform(get("/api/users").param("status", "ACTIVE").session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].loginId").value("admin"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/{id}/approve - 승인")
    class Approve {

        @Test
        void 운영진이_승인하면_ACTIVE_다시_눌러도_200() throws Exception {
            createCohort("A반", "op1");
            User newbie = pending("newbie");

            mockMvc.perform(post("/api/users/" + newbie.getId() + "/approve").session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(newbie.getId()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            mockMvc.perform(post("/api/users/" + newbie.getId() + "/approve").session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            assertThat(userRepository.findById(newbie.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void 없는_사용자는_404_수강생은_403() throws Exception {
            long a = createCohort("A반", "op1");
            mockMvc.perform(post("/api/cohorts/" + a + "/students").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginIds", List.of("s1")))))
                    .andExpect(status().isOk());
            User newbie = pending("newbie");

            mockMvc.perform(post("/api/users/999999/approve").session(login.admin()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/users/" + newbie.getId() + "/approve").session(login.member("s1")))
                    .andExpect(status().isForbidden());
            assertThat(userRepository.findById(newbie.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("승인 대기 게이트")
    class Gate {

        @Test
        void 대기_계정은_me_만_되고_나머지는_403_USER_PENDING() throws Exception {
            User newbie = pending("newbie");

            mockMvc.perform(get("/api/auth/me").session(login.as(newbie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value("newbie"))
                    .andExpect(jsonPath("$.status").value("PENDING"));
            mockMvc.perform(get("/api/me/cohorts").session(login.as(newbie)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_PENDING"));
            mockMvc.perform(get("/api/problems").session(login.as(newbie)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_PENDING"));
            mockMvc.perform(get("/api/notices").session(login.as(newbie)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_PENDING"));
            // 로그아웃은 공개 경로 - 대기 중에도 나갈 수 있어야 한다
            mockMvc.perform(post("/api/auth/logout").session(login.as(newbie)))
                    .andExpect(status().isOk());
        }

        @Test
        void 승인되면_바로_통과() throws Exception {
            User newbie = pending("newbie");
            mockMvc.perform(post("/api/users/" + newbie.getId() + "/approve").session(login.admin()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/me/cohorts").session(login.as(newbie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mockMvc.perform(get("/api/auth/me").session(login.as(newbie)))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        void 수강생으로_배정되면_자동_승인() throws Exception {
            long a = createCohort("A반", "op1");
            User newbie = pending("newbie");

            mockMvc.perform(post("/api/cohorts/" + a + "/students").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginIds", List.of("newbie", "brandnew")))))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(newbie.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
            // 명단으로 선등록된 사람은 처음부터 ACTIVE - 운영진이 넣었다는 것 자체가 승인
            assertThat(userRepository.findByLoginId("brandnew").orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
            mockMvc.perform(get("/api/me/cohorts").session(login.as(newbie)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void 운영진으로_지정되면_자동_승인() throws Exception {
            long a = createCohort("A반");
            User newbie = pending("newbie");

            mockMvc.perform(put("/api/cohorts/" + a + "/operators/newbie").session(login.admin()))
                    .andExpect(status().isOk());

            assertThat(userRepository.findById(newbie.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
            mockMvc.perform(get("/api/users").session(login.as(newbie)))
                    .andExpect(status().isOk());   // 이제 운영진이라 부원 목록도 본다
        }

        @Test
        void 스텁_로그인으로_생긴_계정은_ACTIVE() throws Exception {
            // local·test 의 스텁 로그인은 승인 절차 없이 바로 쓴다 - 개발 편의. 운영(oidc)은 OidcAuthApiTest 에서 PENDING 을 확인한다
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("loginId", "stubby"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }
}
