package kr.haedal.ondal.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 출석 집계 - 한 차시의 명부 요약, 한 학생의 분반 누계 둘 다 이 모양.
 * rate = 출석 ÷ (출석 + 지각 + 결석) × 100 정수 반올림, 판정 0건이면 null (docs attendance/design.md 결정 6). 정책이 바뀌면 여기 한 곳만.
 */
public record AttendanceStats(
        int present,
        int late,
        int absent,
        @Schema(description = "기록 없음 - 명부 요약이면 명단 - 기록, 학생 누계면 분반 차시 - 기록") int unchecked,
        @Schema(description = "출석률(%) - 출석 ÷ 판정된 차시. 판정된 차시가 없으면 null") Integer rate
) {
    public static AttendanceStats of(int present, int late, int absent, int unchecked) {
        int decided = present + late + absent;
        Integer rate = decided == 0 ? null : (int) Math.round(present * 100.0 / decided);
        return new AttendanceStats(present, late, absent, unchecked, rate);
    }
}
