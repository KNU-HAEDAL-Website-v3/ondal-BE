package kr.haedal.hoj.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kr.haedal.hoj.auth.dto.LoginRequest;
import kr.haedal.hoj.auth.dto.UserResponse;
import kr.haedal.hoj.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest httpRequest) {
        User user = authService.login(request.loginId());

        // 세션 고정 공격 방지: 로그인 전 세션은 버리고 새로 발급
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId());

        return UserResponse.from(user);
    }

    /** 프론트가 앱 시작 시 호출해서 로그인 상태·역할을 확인하는 API */
    @GetMapping("/me")
    public UserResponse me(@LoginUser User user) {
        return UserResponse.from(user);
    }

    /** 세션이 이미 없어도 조용히 성공 — 만료된 사용자가 로그아웃을 눌렀을 때 401을 보지 않게 */
    @PostMapping("/logout")
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
