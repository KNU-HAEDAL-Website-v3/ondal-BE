package kr.haedal.ondal.auth.controller;

import kr.haedal.ondal.auth.service.AuthService;
import kr.haedal.ondal.auth.SessionConst;
import kr.haedal.ondal.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.auth.dto.LoginRequest;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인/로그아웃/내 정보 (P1: 스텁 인증)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 공개 경로(AuthPaths.PUBLIC) - 권한 어노테이션 없음 */
    @Operation(summary = "로그인 (스텁: loginId만 보내면 통과, 없으면 MEMBER로 생성)")
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

        // 연관관계 없는 단일 엔티티라 컨트롤러에서 바로 DTO로 바꾼다 - auth만의 예외.
        // 도메인 슬라이스(cohort 등)는 서비스가 DTO를 돌려준다 (docs: cohort/design.md 4절).
        return UserResponse.from(user);
    }

    /** 프론트가 앱 시작 시 호출해서 로그인 상태·역할을 확인하는 API */
    @Operation(summary = "내 정보 (로그인 상태·전역 역할 확인)")
    @LoginOnly
    @GetMapping("/me")
    public UserResponse me(@LoginUser User user) {
        return UserResponse.from(user);
    }

    /** 공개 경로. 세션이 이미 없어도 조용히 성공 - 만료된 사용자가 로그아웃을 눌렀을 때 401을 보지 않게 */
    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
