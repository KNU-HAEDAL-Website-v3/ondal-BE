package kr.haedal.hoj.auth.service;

import kr.haedal.hoj.user.entity.User;

/**
 * "이 loginId가 진짜 그 사람인지" 신원 확인을 담당하는 경계.
 *
 * 이 인터페이스가 스텁 → 홈페이지 연동의 교체 지점이다:
 * - 지금(P1 개발): StubAuthService — 검증 없이 무조건 신뢰
 * - 나중(연동 확정): 홈페이지가 준 토큰/코드를 검증하는 구현으로 이 뒤편만 교체
 * 컨트롤러·세션·나머지 코드는 그대로 둔다.
 */
public interface AuthService {

    User login(String loginId);
}
