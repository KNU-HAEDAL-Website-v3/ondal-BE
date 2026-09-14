package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.attendance.entity.AttendanceStatus;
import kr.haedal.ondal.user.dto.UserResponse;

import java.time.Instant;
import java.util.List;

/**
 * 차시 출석 명부 (운영진 이상) - 행 = 현재 STUDENT 명단(이름순), 표시 API 응답도 이 모양이라 재조회가 필요 없다.
 * user 는 loginId 를 포함한다(운영진만 보는 응답 - 표시 요청에 loginId 가 필요).
 */
public record AttendanceRosterResponse(
        SessionResponse session,
        @Schema(description = "이 차시 요약 - unchecked = 명단 수 - 기록 수") AttendanceStats summary,
        List<AttendanceRow> rows
) {
    public record AttendanceRow(
            UserResponse user,
            @Schema(description = "이 차시의 판정 - 기록 없으면 null(미확인)") AttendanceStatus status,
            @Schema(description = "표시(마지막 변경) 시각 - 기록 없으면 null") Instant checkedAt,
            @Schema(description = "이 학생의 분반 전체 누계 - 출석률 열") AttendanceStats stats
    ) {
    }
}
