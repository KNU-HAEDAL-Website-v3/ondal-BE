package kr.haedal.hoj.support;

import kr.haedal.hoj.auth.SessionConst;
import kr.haedal.hoj.user.entity.User;
import kr.haedal.hoj.user.repository.UserRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Component;

/**
 * 테스트에서 "이 사람으로 로그인된 상태"를 만든다.
 * 실제 로그인 API를 부르지 않고 세션 속성을 직접 넣는다 - 인증 방식이 홈페이지 연동으로 바뀌어도 이 헬퍼는 그대로다.
 * (로그인 API 자체의 동작은 AuthApiTest 가 따로 검증한다)
 *
 * 사용: mockMvc.perform(get("/api/me/cohorts").session(login.member("student1")))
 */
@Component
public class LoginHelper {

    private final UserRepository userRepository;

    public LoginHelper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 이미 존재하는 User로 로그인된 세션 */
    public MockHttpSession as(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId());
        return session;
    }

    /** loginId의 MEMBER를 (없으면 만들고) 로그인된 세션으로 */
    public MockHttpSession member(String loginId) {
        return as(memberUser(loginId));
    }

    /** "admin" ADMIN 계정을 (없으면 만들고) 로그인된 세션으로 - permissions.md 4절 부트스트랩의 테스트 버전 */
    public MockHttpSession admin() {
        return as(adminUser());
    }

    public User memberUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseGet(() -> userRepository.save(User.member(loginId, loginId)));
    }

    public User adminUser() {
        return userRepository.findByLoginId("admin")
                .orElseGet(() -> userRepository.save(User.admin("admin", "관리자")));
    }
}
