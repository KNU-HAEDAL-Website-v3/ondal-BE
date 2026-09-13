package kr.haedal.ondal.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.SessionConst;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.auth.dto.LogoutResponse;
import kr.haedal.ondal.auth.service.OidcAuthService;
import kr.haedal.ondal.user.dto.UserResponse;
import kr.haedal.ondal.user.entity.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 모드와 무관한 공통 API - 내 정보·로그아웃.
 * 로그인은 모드별 컨트롤러가 담당한다: StubAuthController(POST /login, local·test) / OidcAuthController(GET /login·/callback, prod).
 * 세션 발급 규칙은 LoginSession 한 곳에 있다.
 */
@Tag(name = "Auth", description = "로그인/로그아웃/내 정보 - 로그인 방식은 ondal.auth.mode (stub: POST /login / oidc: GET /login → 홈페이지 Keycloak)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** oidc 모드에서만 존재하는 빈 - 로그아웃 때 홈페이지(SSO) 로그아웃 주소를 계산하는 데만 쓴다. stub 모드면 비어 있다 */
    private final ObjectProvider<OidcAuthService> oidcAuthService;

    public AuthController(ObjectProvider<OidcAuthService> oidcAuthService) {
        this.oidcAuthService = oidcAuthService;
    }

    /** 프론트가 앱 시작 시 호출해서 로그인 상태·역할을 확인하는 API */
    @Operation(summary = "내 정보 (로그인 상태·전역 역할 확인)")
    @LoginOnly
    @GetMapping("/me")
    public UserResponse me(@LoginUser User user) {
        // 연관관계 없는 단일 엔티티라 컨트롤러에서 바로 DTO로 바꾼다 - auth만의 예외.
        // 도메인 슬라이스(cohort 등)는 서비스가 DTO를 돌려준다 (docs: cohort/design.md 4절).
        return UserResponse.from(user);
    }

    /**
     * 공개 경로. 세션이 이미 없어도 조용히 성공 - 만료된 사용자가 로그아웃을 눌렀을 때 401을 보지 않게.
     * oidc 모드면 홈페이지(Keycloak) 세션까지 끝낼 주소(logoutUrl)를 돌려준다 - FE 가 그 주소로 이동해야 SSO 로그아웃이 완성된다.
     * 공용 PC(실습실)에서 다음 사람이 "로그인" 버튼만 눌러 앞사람 계정으로 자동 로그인되는 것을 막기 위함.
     */
    @Operation(summary = "로그아웃 - Ondal 세션 종료. oidc 모드면 홈페이지(SSO) 로그아웃 주소 logoutUrl 을 함께 돌려준다 (FE 가 이동)")
    @PostMapping("/logout")
    public LogoutResponse logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return new LogoutResponse(null);
        }
        Object idToken = session.getAttribute(SessionConst.OIDC_ID_TOKEN);
        session.invalidate();

        OidcAuthService oidc = oidcAuthService.getIfAvailable();
        String logoutUrl = oidc == null ? null : oidc.logoutUrl(idToken instanceof String raw ? raw : null);
        return new LogoutResponse(logoutUrl);
    }
}
