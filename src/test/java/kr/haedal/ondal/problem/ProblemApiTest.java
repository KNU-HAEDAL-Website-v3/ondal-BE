package kr.haedal.ondal.problem;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문제 라이브러리 API (ProblemController, HOJ) - V7.
 *
 * 문제는 분반에 속하지 않는다: 조회는 로그인한 누구나, 출제·수정·삭제는 "어느 분반에서든 운영진"(@OperatorAnywhere).
 * 문제 번호 채번·중복 규칙은 과제에서 여기로 옮겨 왔다 (schema.md 결정 9 승계).
 */
class ProblemApiTest extends ApiTestSupport {

    private static final Instant FUTURE_DUE = Instant.now().plus(7, ChronoUnit.DAYS);

    // ---- 픽스처 --------------------------------------------------------------------------

    private Map<String, Object> problemBody(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", title + " 본문");
        return body;
    }

    private long createTag(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tags").session(login.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Nested
    @DisplayName("문제 번호 - 전역 유일, 1000부터 (schema.md 결정 9)")
    class ProblemNo {

        @Test
        void 비우면_1000부터_자동_채번() throws Exception {
            createCohort("C언어", "op1");
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("첫 문제"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.problemNo").value(1000));
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("둘째 문제"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.problemNo").value(1001));
        }

        @Test
        void 수동_지정하면_그_번호_이후_자동은_최대_더하기_1() throws Exception {
            createCohort("C언어", "op1");
            Map<String, Object> manual = problemBody("수동 번호");
            manual.put("problemNo", 2000);
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(manual)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.problemNo").value(2000));
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("자동 번호"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.problemNo").value(2001));
        }

        @Test
        void 중복_지정은_409_천_미만은_400() throws Exception {
            createCohort("C언어", "op1");
            Map<String, Object> manual = problemBody("선점");
            manual.put("problemNo", 2000);
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(manual)))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(manual)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));

            Map<String, Object> tooSmall = problemBody("번호 오류");
            tooSmall.put("problemNo", 999);
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(tooSmall)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 수정에서_비우면_기존_번호_유지_지정하면_변경_타_문제_번호는_409() throws Exception {
            createCohort("C언어", "op1");
            long first = createProblem("첫 문제");    // 1000
            long second = createProblem("둘째 문제"); // 1001

            mockMvc.perform(put("/api/problems/{id}", second).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("번호 없이 수정"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.problemNo").value(1001))
                    .andExpect(jsonPath("$.title").value("번호 없이 수정"));

            Map<String, Object> renumber = problemBody("번호 바꿔 수정");
            renumber.put("problemNo", 3000);
            mockMvc.perform(put("/api/problems/{id}", second).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(renumber)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.problemNo").value(3000));

            // 자기 번호를 다시 보내는 것은 중복이 아니다
            mockMvc.perform(put("/api/problems/{id}", second).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(renumber)))
                    .andExpect(status().isOk());

            Map<String, Object> clash = problemBody("충돌 시도");
            clash.put("problemNo", 1000);
            mockMvc.perform(put("/api/problems/{id}", second).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(clash)))
                    .andExpect(status().isConflict());
            // 선점한 쪽은 그대로
            mockMvc.perform(get("/api/problems/{id}", first).session(login.member("op1")))
                    .andExpect(jsonPath("$.problemNo").value(1000));
        }
    }

    @Nested
    @DisplayName("권한 - 조회는 누구나, 출제는 운영진 이상")
    class Authorization {

        @Test
        void 미로그인이면_401() throws Exception {
            mockMvc.perform(get("/api/problems")).andExpect(status().isUnauthorized());
        }

        @Test
        void 분반에_속하지_않은_부원도_목록과_상세를_볼_수_있다() throws Exception {
            createCohort("C언어", "op1");
            long problemId = createProblem("공개 문제");
            // HOJ 는 "지금까지 만든 문제를 모아 보는 곳" - 분반 소속과 무관하게 열린다
            mockMvc.perform(get("/api/problems").session(login.member("outsider")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("outsider")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("공개 문제"))
                    .andExpect(jsonPath("$.canEdit").value(false));
        }

        @Test
        void 수강생은_출제_수정_삭제_403() throws Exception {
            long cohortId = createCohort("C언어", "op1");
            enrollStudent(cohortId, "s1");
            long problemId = createProblem("문제");

            mockMvc.perform(post("/api/problems").session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("학생 출제"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/problems/{id}", problemId).session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("학생 수정"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/problems/{id}", problemId).session(login.member("s1")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canEdit").value(false));
        }

        @Test
        void 어느_분반에서든_운영진이면_출제할_수_있다() throws Exception {
            createCohort("C언어", "op1");
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("운영진 출제"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/api/problems/")))
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andExpect(jsonPath("$.createdBy").value("op1"));
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        void 제목_공백이나_200자_초과는_400() throws Exception {
            createCohort("C언어", "op1");
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody(" "))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(problemBody("가".repeat(201)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 없는_문제는_404() throws Exception {
            createCohort("C언어", "op1");
            mockMvc.perform(get("/api/problems/{id}", 999_999).session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("태그 - 통째 교체, 목록 필터는 AND")
    class Tags {

        @Test
        void 태그를_붙이고_통째로_교체한다() throws Exception {
            createCohort("C언어", "op1");
            long dp = createTag("DP");
            long graph = createTag("그래프");

            Map<String, Object> body = problemBody("태그 문제");
            body.put("tagIds", List.of(dp, graph));
            MvcResult created = mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tags", hasSize(2)))
                    .andReturn();
            long problemId = readJson(created).get("id").asLong();

            // 통째 교체 - 준 목록이 곧 결과
            Map<String, Object> replace = problemBody("태그 문제");
            replace.put("tagIds", List.of(graph));
            mockMvc.perform(put("/api/problems/{id}", problemId).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(replace)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tags", hasSize(1)))
                    .andExpect(jsonPath("$.tags[0].name").value("그래프"));

            // 빈 목록이면 태그 없음
            Map<String, Object> clear = problemBody("태그 문제");
            clear.put("tagIds", List.of());
            mockMvc.perform(put("/api/problems/{id}", problemId).session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(clear)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tags", hasSize(0)));
        }

        @Test
        void 없는_태그를_붙이면_400() throws Exception {
            createCohort("C언어", "op1");
            Map<String, Object> body = problemBody("문제");
            body.put("tagIds", List.of(999_999));
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 목록_태그_필터는_고른_태그를_모두_가진_문제만() throws Exception {
            createCohort("C언어", "op1");
            long dp = createTag("DP");
            long graph = createTag("그래프");

            Map<String, Object> both = problemBody("둘 다");
            both.put("tagIds", List.of(dp, graph));
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                    .contentType(MediaType.APPLICATION_JSON).content(json(both))).andExpect(status().isCreated());

            Map<String, Object> onlyDp = problemBody("DP 만");
            onlyDp.put("tagIds", List.of(dp));
            mockMvc.perform(post("/api/problems").session(login.member("op1"))
                    .contentType(MediaType.APPLICATION_JSON).content(json(onlyDp))).andExpect(status().isCreated());

            mockMvc.perform(get("/api/problems").param("tagIds", String.valueOf(dp)).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
            mockMvc.perform(get("/api/problems")
                            .param("tagIds", String.valueOf(dp))
                            .param("tagIds", String.valueOf(graph))
                            .session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].title").value("둘 다"));
        }
    }

    @Nested
    @DisplayName("배정과 삭제 - 쓰이는 문제는 지울 수 없다")
    class Deletion {

        @Test
        void 한_번도_안_쓴_문제는_삭제된다() throws Exception {
            createCohort("C언어", "op1");
            long problemId = createProblem("안 쓴 문제");
            mockMvc.perform(delete("/api/problems/{id}", problemId).session(login.member("op1")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 배정된_문제는_409_과제를_지우면_삭제된다() throws Exception {
            long cohortId = createCohort("C언어", "op1");
            long problemId = createProblem("배정된 문제");
            MvcResult assigned = mockMvc.perform(post("/api/cohorts/{id}/assignments", cohortId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("problemId", problemId, "dueAt", FUTURE_DUE.toString()))))
                    .andExpect(status().isCreated())
                    .andReturn();
            long assignmentId = readJson(assigned).get("id").asLong();

            mockMvc.perform(delete("/api/problems/{id}", problemId).session(login.member("op1")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));

            mockMvc.perform(delete("/api/cohorts/{cid}/assignments/{aid}", cohortId, assignmentId)
                            .session(login.member("op1")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/problems/{id}", problemId).session(login.member("op1")))
                    .andExpect(status().isNoContent());
        }

        @Test
        void 같은_문제를_여러_분반에_배정하면_배정_횟수가_쌓인다() throws Exception {
            long a = createCohort("A반", "op1");
            long b = createCohort("B반", "op1");
            long problemId = createProblem("공용 문제");
            for (long cohortId : List.of(a, b)) {
                mockMvc.perform(post("/api/cohorts/{id}/assignments", cohortId).session(login.member("op1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("problemId", problemId, "dueAt", FUTURE_DUE.toString()))))
                        .andExpect(status().isCreated());
            }
            mockMvc.perform(get("/api/problems/{id}", problemId).session(login.member("op1")))
                    .andExpect(jsonPath("$.assignedCount").value(2));
            mockMvc.perform(get("/api/problems").session(login.member("op1")))
                    .andExpect(jsonPath("$[0].assignedCount").value(2));
        }
    }
}
