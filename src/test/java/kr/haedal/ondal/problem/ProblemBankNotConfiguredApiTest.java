package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 레포 토큰이 없는 기본 설정(test 프로필 = application.yml 의 빈 token) - 화면은 파일 업로드만 안내하고, 가져오기는 503 */
@DisplayName("문제 은행 레포 토큰 미설정")
class ProblemBankNotConfiguredApiTest extends ApiTestSupport {

    @Test
    void 설정_조회는_configured_false_가져오기는_503() throws Exception {
        mockMvc.perform(get("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.repo").value("KNU-HAEDAL-Website-v3/ondal-problems"))
                .andExpect(jsonPath("$.ref").value("main"));

        mockMvc.perform(post("/api/problems/import/github").session(login.admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROBLEM_BANK_NOT_CONFIGURED"));
    }
}
