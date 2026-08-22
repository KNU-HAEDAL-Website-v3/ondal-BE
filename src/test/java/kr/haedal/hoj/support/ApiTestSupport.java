package kr.haedal.hoj.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import kr.haedal.hoj.cohort.entity.Cohort;
import kr.haedal.hoj.cohort.repository.CohortRepository;
import kr.haedal.hoj.enrollment.entity.Enrollment;
import kr.haedal.hoj.enrollment.repository.EnrollmentRepository;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.user.entity.User;
import kr.haedal.hoj.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 테스트의 공통 베이스. 각 슬라이스 테스트는 `extends ApiTestSupport` 한 줄로 시작한다.
 *
 * - @SpringBootTest + MockMvc: 실제 서블릿 스택(인터셉터·리졸버·예외 핸들러)을 다 태운다
 * - @ActiveProfiles("test"): local 프로필의 LocalDataSeeder 가 돌지 않는다. 픽스처는 각 테스트가 만든다
 * - PostgreSQL 은 Testcontainers 로 1번 띄우고, 매 테스트 후 DatabaseCleaner 가 전부 비운다
 * - 인증/권한 상태는 LoginHelper 로 만든다 (login.admin(), login.member("x"))
 *
 * 이 파일과 support/ 의 나머지는 PM 담당 — 슬라이스 테스트를 쓰는 사람은 수정하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
public abstract class ApiTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected LoginHelper login;
    @Autowired protected CohortRepository cohortRepository;
    @Autowired protected EnrollmentRepository enrollmentRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    // ---- 자주 쓰는 픽스처 -------------------------------------------------------------

    /** 관리자 API 로 분반을 만들고 id 를 돌려준다 (운영진 loginId 는 선택) */
    protected long createCohort(String name, String... operatorLoginIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts")
                        .session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", name + " 설명",
                                "operatorLoginIds", List.of(operatorLoginIds)))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** 수강생 배정 API(운영진 이상)로 소속시킨다 — admin 세션 사용. ACTIVE 분반에만 쓸 것(보관이면 409) */
    protected User enrollStudent(long cohortId, String loginId) throws Exception {
        mockMvc.perform(post("/api/cohorts/{id}/students", cohortId)
                        .session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginIds", List.of(loginId)))))
                .andExpect(status().isOk());
        return login.memberUser(loginId);
    }

    /** 관리자 API 로 보관 처리 */
    protected void archiveCohort(long cohortId) throws Exception {
        mockMvc.perform(post("/api/cohorts/{id}/archive", cohortId).session(login.admin()))
                .andExpect(status().isOk());
    }

    /** 관리자 API 로 보관 해제 */
    protected void restoreCohort(long cohortId) throws Exception {
        mockMvc.perform(post("/api/cohorts/{id}/restore", cohortId).session(login.admin()))
                .andExpect(status().isOk());
    }

    // ---- JSON 유틸 -----------------------------------------------------------------------

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
