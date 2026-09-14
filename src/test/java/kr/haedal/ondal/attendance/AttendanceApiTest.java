package kr.haedal.ondal.attendance;

import tools.jackson.databind.JsonNode;
import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

/** 출석부 API (SessionController #34~#37, AttendanceController #38~#40) */
class AttendanceApiTest extends ApiTestSupport {

    // ---- 슬라이스 고유 픽스처 (support/는 PM 파일 - 여기 private 헬퍼로) ----------------------

    private Map<String, Object> sessionBody(Integer sessionNo, String heldOn) {
        Map<String, Object> body = new HashMap<>();
        if (sessionNo != null) body.put("sessionNo", sessionNo);
        body.put("title", "차시 제목");
        body.put("heldOn", heldOn);
        return body;
    }

    private long createSession(long cohortId, MockHttpSession operator, Integer sessionNo, String heldOn) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/sessions", cohortId)
                        .session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sessionBody(sessionNo, heldOn))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    /** loginId → status(null 이면 기록 삭제) 를 records 로 - 순서 유지 */
    private Map<String, Object> markBody(LinkedHashMap<String, String> marks) {
        List<Map<String, Object>> records = new ArrayList<>();
        marks.forEach((loginId, statusOrNull) -> {
            Map<String, Object> r = new HashMap<>();
            r.put("loginId", loginId);
            r.put("status", statusOrNull);
            records.add(r);
        });
        return Map.of("records", records);
    }

    private JsonNode mark(long cohortId, long sessionId, MockHttpSession operator, LinkedHashMap<String, String> marks) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}/attendances", cohortId, sessionId)
                        .session(operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(markBody(marks))))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result);
    }

    private static LinkedHashMap<String, String> marks(String... loginIdStatusPairs) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < loginIdStatusPairs.length; i += 2) {
            map.put(loginIdStatusPairs[i], loginIdStatusPairs[i + 1]);
        }
        return map;
    }

    private static JsonNode row(JsonNode roster, String loginId) {
        for (JsonNode row : roster.get("rows")) {
            if (row.get("user").get("loginId").asText().equals(loginId)) return row;
        }
        throw new AssertionError("명부에 없음: " + loginId);
    }

    @Nested
    @DisplayName("차시 (#34~#37)")
    class Sessions {

        @Test
        void 등록은_운영진_이상_번호_자동채번_중복은_409() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");

            for (MockHttpSession denied : new MockHttpSession[]{login.member("s1"), login.member("outsider")}) {
                mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                                .session(denied)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(sessionBody(null, "2026-09-16"))))
                        .andExpect(status().isForbidden());
            }

            mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(null, "2026-09-16"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/cohorts/" + id + "/sessions/")))
                    .andExpect(jsonPath("$.sessionNo").value(1))
                    .andExpect(jsonPath("$.heldOn").value("2026-09-16"))
                    .andExpect(jsonPath("$.attendanceCount").value(0));
            mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(null, "2026-09-18"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sessionNo").value(2));
            // 지정 번호 중복 → 409
            mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(2, "2026-09-20"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
            // 검증: 날짜 누락 / 번호 0
            mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "날짜 없음"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(0, "2026-09-20"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 목록은_소속자_날짜_번호_오름차순_비소속_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            createSession(id, login.member("op1"), 2, "2026-09-10");
            createSession(id, login.member("op1"), 1, "2026-09-03");

            mockMvc.perform(get("/api/cohorts/{id}/sessions", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].sessionNo").value(1))
                    .andExpect(jsonPath("$[1].sessionNo").value(2));
            mockMvc.perform(get("/api/cohorts/{id}/sessions", id).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 수정_번호중복_409_삭제는_기록_연쇄() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long first = createSession(id, login.member("op1"), 1, "2026-09-03");
            long second = createSession(id, login.member("op1"), 2, "2026-09-10");
            mark(id, second, login.member("op1"), marks("s1", "PRESENT"));

            mockMvc.perform(get("/api/cohorts/{id}/sessions", id).session(login.member("op1")))
                    .andExpect(jsonPath("$[1].attendanceCount").value(1));
            // 다른 차시 번호로 바꾸면 409, 자기 번호 유지 + 제목·날짜 변경은 200
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}", id, second)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(1, "2026-09-11"))))
                    .andExpect(status().isConflict());
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}", id, second)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(2, "2026-09-11"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.heldOn").value("2026-09-11"))
                    .andExpect(jsonPath("$.attendanceCount").value(1));
            // 삭제 → 기록도 사라지고 명부 404
            mockMvc.perform(delete("/api/cohorts/{id}/sessions/{sid}", id, second).session(login.member("op1")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/cohorts/{id}/sessions/{sid}/attendances", id, second).session(login.member("op1")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/cohorts/{id}/sessions", id).session(login.member("op1")))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(first));
        }

        @Test
        void 다른_분반의_차시는_404() throws Exception {
            long a = createCohort("A반", "opA");
            long b = createCohort("B반", "opA");
            long sessionOfA = createSession(a, login.member("opA"), 1, "2026-09-03");

            mockMvc.perform(get("/api/cohorts/{id}/sessions/{sid}/attendances", b, sessionOfA).session(login.member("opA")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/cohorts/{id}/sessions/{sid}", b, sessionOfA).session(login.member("opA")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/cohorts/{id}/sessions/{sid}/attendances", a, sessionOfA).session(login.member("opA")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("출석 (#38~#40)")
    class Attendances {

        @Test
        void 명부는_운영진_이상_학생_403_초기는_전원_미확인() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s2");
            enrollStudent(id, "s1");
            long sessionId = createSession(id, login.member("op1"), 1, "2026-09-03");

            mockMvc.perform(get("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId).session(login.member("s1")))
                    .andExpect(status().isForbidden());

            MvcResult result = mockMvc.perform(get("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.session.sessionNo").value(1))
                    .andExpect(jsonPath("$.rows", hasSize(2)))
                    .andExpect(jsonPath("$.rows[0].user.loginId").value("s1"))   // 이름순 (운영진 op1 은 행에 없음)
                    .andExpect(jsonPath("$.rows[0].status").isEmpty())
                    .andExpect(jsonPath("$.summary.unchecked").value(2))
                    .andExpect(jsonPath("$.summary.rate").isEmpty())
                    .andReturn();
            assertThat(readJson(result).get("rows").get(0).get("stats").get("unchecked").asInt()).isEqualTo(1);
        }

        @Test
        void 표시_upsert_마지막값_삭제_누계() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            enrollStudent(id, "s3");
            long sessionId = createSession(id, login.member("op1"), 1, "2026-09-03");

            JsonNode roster = mark(id, sessionId, login.member("op1"), marks("s1", "PRESENT", "s2", "LATE"));
            assertThat(row(roster, "s1").get("status").asText()).isEqualTo("PRESENT");
            assertThat(row(roster, "s2").get("status").asText()).isEqualTo("LATE");
            assertThat(row(roster, "s3").get("status").isNull()).isTrue();
            assertThat(roster.get("summary").get("present").asInt()).isEqualTo(1);
            assertThat(roster.get("summary").get("late").asInt()).isEqualTo(1);
            assertThat(roster.get("summary").get("unchecked").asInt()).isEqualTo(1);
            assertThat(roster.get("summary").get("rate").asInt()).isEqualTo(50);   // 출석 1 / 판정 2
            assertThat(row(roster, "s1").get("stats").get("rate").asInt()).isEqualTo(100);
            assertThat(row(roster, "s2").get("stats").get("rate").asInt()).isEqualTo(0);
            assertThat(row(roster, "s1").get("checkedAt").isNull()).isFalse();

            // 같은 loginId 두 번 → 마지막 값 / status null → 기록 삭제
            roster = mark(id, sessionId, login.member("op1"), marks("s2", "PRESENT", "s2", "ABSENT", "s1", null));
            assertThat(row(roster, "s2").get("status").asText()).isEqualTo("ABSENT");
            assertThat(row(roster, "s1").get("status").isNull()).isTrue();
            assertThat(roster.get("summary").get("unchecked").asInt()).isEqualTo(2);

            // 운영진·모르는 아이디 → 404, 빈 목록·잘못된 상태 → 400
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(markBody(marks("op1", "PRESENT")))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("records", List.of()))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("records", List.of(Map.of("loginId", "s1", "status", "WHAT"))))))
                    .andExpect(status().isBadRequest());
            // 학생은 표시 불가
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(markBody(marks("s1", "PRESENT")))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 내_출석은_최신_차시_먼저_누계_포함_비소속_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long first = createSession(id, login.member("op1"), 1, "2026-09-03");
            createSession(id, login.member("op1"), 2, "2026-09-10");
            mark(id, first, login.member("op1"), marks("s1", "PRESENT"));

            mockMvc.perform(get("/api/cohorts/{id}/attendances/me", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.present").value(1))
                    .andExpect(jsonPath("$.summary.unchecked").value(1))
                    .andExpect(jsonPath("$.summary.rate").value(100))
                    .andExpect(jsonPath("$.records", hasSize(2)))
                    .andExpect(jsonPath("$.records[0].session.sessionNo").value(2))
                    .andExpect(jsonPath("$.records[0].status").isEmpty())
                    .andExpect(jsonPath("$.records[1].status").value("PRESENT"));
            mockMvc.perform(get("/api/cohorts/{id}/attendances/me", id).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
            // 운영진이 부르면 기록 없이 차시만
            mockMvc.perform(get("/api/cohorts/{id}/attendances/me", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.rate").isEmpty())
                    .andExpect(jsonPath("$.records", hasSize(2)));
        }
    }

    @Nested
    @DisplayName("보관 분반")
    class Archived {

        @Test
        void 보관_분반은_열람_유지_쓰기_409_해제_후_복구() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long sessionId = createSession(id, login.member("op1"), 1, "2026-09-03");
            mark(id, sessionId, login.member("op1"), marks("s1", "LATE"));
            archiveCohort(id);

            mockMvc.perform(get("/api/cohorts/{id}/sessions", id).session(login.member("s1"))).andExpect(status().isOk());
            mockMvc.perform(get("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId).session(login.member("op1")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/cohorts/{id}/attendances/me", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.late").value(1));

            mockMvc.perform(post("/api/cohorts/{id}/sessions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(null, "2026-09-10"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}", id, sessionId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(sessionBody(1, "2026-09-04"))))
                    .andExpect(status().isConflict());
            mockMvc.perform(delete("/api/cohorts/{id}/sessions/{sid}", id, sessionId).session(login.member("op1")))
                    .andExpect(status().isConflict());
            mockMvc.perform(put("/api/cohorts/{id}/sessions/{sid}/attendances", id, sessionId)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(markBody(marks("s1", "PRESENT")))))
                    .andExpect(status().isConflict());

            restoreCohort(id);
            mark(id, sessionId, login.member("op1"), marks("s1", "PRESENT"));
        }
    }
}
