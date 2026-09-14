package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.attendance.entity.AttendanceStatus;

import java.time.Instant;
import java.util.List;

/** 내 출석 (학생) - 분반 누계 + 차시별 기록(최신 차시 먼저). 관리자·운영진이 부르면 기록 없는 차시 목록만 온다 */
public record MyAttendanceResponse(
        @Schema(description = "분반 누계 - unchecked = 분반 차시 수 - 내 기록 수") AttendanceStats summary,
        List<MyAttendanceRecord> records
) {
    public record MyAttendanceRecord(
            SessionResponse session,
            @Schema(description = "판정 - 기록 없으면 null(미확인)") AttendanceStatus status,
            Instant checkedAt
    ) {
    }
}
