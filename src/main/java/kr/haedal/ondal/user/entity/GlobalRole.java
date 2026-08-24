package kr.haedal.ondal.user.entity;

/**
 * 전역 역할 - "사람에게" 붙는 속성.
 * 분반 안에서의 역할(OPERATOR/STUDENT)은 Enrollment.role이 담당한다. (docs: permissions.md)
 */
public enum GlobalRole {
    ADMIN,   // 동아리 임원 - 모든 분반에서 운영자 이상
    MEMBER   // 일반 부원
}
