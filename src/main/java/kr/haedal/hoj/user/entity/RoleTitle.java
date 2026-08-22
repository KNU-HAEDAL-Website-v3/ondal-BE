package kr.haedal.hoj.user.entity;

import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import com.fasterxml.jackson.annotation.JsonValue; // Jackson 3 에서도 어노테이션 패키지는 com.fasterxml 유지

/**
 * 화면에 표시하는 직책 명칭 — 동아리 용어를 코드 한 곳에서 관리한다. API 는 label 문자열을 그대로 내려주고 프론트는 표시만 한다.
 *
 * | 직책 | 누구 | 명칭 |
 * |---|---|---|
 * | EXECUTIVE | 동아리 임원단 (User.globalRole = ADMIN) | 해구르르 — 고정 |
 * | OPERATOR  | 부트캠프 분반을 맡은 학생 (Enrollment.role = OPERATOR) | 교육운영진 — 고정 |
 * | MEMBER    | 그 외 (수강생, 미소속 부원) | 일반 수강생 — 바뀔 수 있음. 아래 label 한 줄만 고치면 전체 반영 |
 *
 * 판정 우선순위: 전역 ADMIN 이면 어느 분반에서든 해구르르 → 분반 OPERATOR 면 교육운영진 → 나머지.
 * (permissions.md 의 "임원진 / 교육 운영진 / 교육생" 이 이 세 이름이다)
 */
public enum RoleTitle {

    EXECUTIVE("해구르르"),
    OPERATOR("교육운영진"),
    MEMBER("일반 수강생");   // ← 이 명칭은 미확정. 바꾸려면 여기만 수정

    private final String label;

    RoleTitle(String label) {
        this.label = label;
    }

    /** JSON 으로는 명칭 문자열 그대로 나간다 (예: "해구르르") — 프론트에 enum 이름을 알릴 필요가 없다 */
    @JsonValue
    public String label() {
        return label;
    }

    /** 이 사람이 이 분반(또는 분반 무관 문맥이면 roleOrNull = null)에서 어떤 직책으로 보이는가 */
    public static RoleTitle of(User user, EnrollmentRole roleOrNull) {
        if (user.isAdmin()) {
            return EXECUTIVE;
        }
        if (roleOrNull == EnrollmentRole.OPERATOR) {
            return OPERATOR;
        }
        return MEMBER;
    }
}
