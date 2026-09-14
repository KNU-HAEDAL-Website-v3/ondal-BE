package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.attendance.entity.Session;

import java.time.Instant;
import java.time.LocalDate;

/** 차시 응답 - 목록·등록·수정과 명부·내 출석 안에 실린다 */
public record SessionResponse(
        Long id,
        Integer sessionNo,
        String title,
        @Schema(description = "수업 날짜 (KST 달력일, yyyy-MM-dd)") LocalDate heldOn,
        Instant createdAt,
        @Schema(description = "이 차시의 출석 기록 수 - 삭제 경고(\"기록 N건 함께 삭제\")용") long attendanceCount
) {
    public static SessionResponse of(Session session, long attendanceCount) {
        return new SessionResponse(session.getId(), session.getSessionNo(), session.getTitle(),
                session.getHeldOn(), session.getCreatedAt(), attendanceCount);
    }
}
