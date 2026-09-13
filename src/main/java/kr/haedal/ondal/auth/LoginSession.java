package kr.haedal.ondal.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.haedal.ondal.user.entity.User;

/**
 * "이 사람으로 로그인된 세션"을 만드는 단 하나의 방법 - 스텁 로그인과 홈페이지(OIDC) 로그인이 같이 쓴다.
 * 세션 고정 공격 방지: 로그인 전 세션(스텁의 빈 세션, OIDC 의 PendingLogin 세션)은 버리고 새 id 로 발급한 뒤 사용자 id 를 넣는다.
 * 세션에는 User 엔티티가 아니라 id 만 넣는다 (SessionConst 참고).
 */
public final class LoginSession {

    private LoginSession() {
    }

    public static HttpSession establish(HttpServletRequest request, User user) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId());
        return session;
    }
}
