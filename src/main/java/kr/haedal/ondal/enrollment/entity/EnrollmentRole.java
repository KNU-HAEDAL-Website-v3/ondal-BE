package kr.haedal.ondal.enrollment.entity;

/**
 * 분반 안에서의 역할 - "사람-분반 관계에" 붙는 속성. (docs: permissions.md 1절)
 * 전역 역할(ADMIN/MEMBER)은 User.globalRole이 담당한다.
 */
public enum EnrollmentRole {
    OPERATOR,   // 교육 운영진 - 이 분반의 과제·수강생·현황을 관리
    STUDENT;    // 교육생 - 자기 분반의 과제만 보고 제출

    /** OPERATOR는 STUDENT가 할 수 있는 것을 전부 할 수 있다 (OPERATOR ⊇ STUDENT) */
    public boolean satisfies(EnrollmentRole required) {
        if (required == STUDENT) {
            return true;
        }
        return this == OPERATOR;
    }
}
