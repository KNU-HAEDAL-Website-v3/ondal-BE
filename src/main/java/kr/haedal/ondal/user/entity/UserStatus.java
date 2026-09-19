package kr.haedal.ondal.user.entity;

/**
 * 계정 상태 - "부원임을 운영진이 확인했는가" (docs 결정 10, 2026-09-19).
 *
 * 홈페이지(구글) 로그인만으로 Ondal 화면에 들어오면 안 된다는 PM 결정의 구현. 판정 순서(permissions.md 2절)에서
 * ① 로그인 여부 다음, ② 역할 판정 전에 놓이는 문턱 - AuthorizationInterceptor 가 PENDING 이면 @PendingAllowed API 만 통과시킨다.
 *
 * PENDING → ACTIVE 로만 바뀐다(되돌림 없음). 활성화 경로 3가지:
 *   1. 운영진 이상이 부원 목록에서 승인 (POST /api/users/{id}/approve)
 *   2. 운영진 이상이 분반에 배정 (수강생 배정·운영진 지정) - 배정이 곧 "우리 교육생" 확인이므로 따로 누르지 않는다
 *   3. 처음부터 ACTIVE 로 만들어진 경우 - 명부 선등록(findOrCreateMember), 스텁 로그인(local·test), 부트스트랩 관리자
 */
public enum UserStatus {
    PENDING,  // 승인 대기 - 첫 홈페이지(OIDC) 로그인으로 생긴 계정
    ACTIVE    // 이용 가능
}
