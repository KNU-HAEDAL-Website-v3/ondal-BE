package kr.haedal.ondal.user.entity;

/**
 * 전역 역할 - "사람에게" 붙는 속성.
 * 분반 안에서의 역할(OPERATOR/STUDENT)은 Enrollment.role이 담당한다. (docs: permissions.md)
 *
 * ADMIN 과 MAINTAINER 는 권한이 같다(User.isAdmin 이 둘 다 true) - 다른 것은 표시 명칭(RoleTitle)뿐.
 * 권한 판정은 반드시 User.isAdmin() 으로 하고, 이 enum 값을 직접 비교하지 않는다 (docs 결정 12).
 */
public enum GlobalRole {
    ADMIN,        // 동아리 임원(해구르르) - 모든 분반에서 운영자 이상
    MAINTAINER,   // 유지보수 팀(관리자) - 해구르르와 같은 권한, 동아리 임원은 아님 (2026-09-19 PM, 결정 12)
    MEMBER        // 일반 부원
}
