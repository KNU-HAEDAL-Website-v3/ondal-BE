package kr.haedal.ondal.attendance.entity;

/**
 * 한 차시에서 한 수강생의 출석 판정 - 운영진이 표시한다.
 * "미확인"은 여기 없다 - 기록이 없는 상태(응답 status null) (docs attendance/design.md 결정 2).
 * FE 는 이 값을 그대로 배지에 매핑한다 - 프론트 재계산 금지.
 */
public enum AttendanceStatus {
    /** 정시 출석 - 출석률 분자 */
    PRESENT,
    /** 지각 - 출석률에는 넣지 않고 횟수로 따로 보인다 (결정 6) */
    LATE,
    /** 결석 */
    ABSENT
}
