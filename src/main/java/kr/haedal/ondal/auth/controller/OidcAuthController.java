package kr.haedal.ondal.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.haedal.ondal.auth.AuthMode;
import kr.haedal.ondal.auth.LoginSession;
import kr.haedal.ondal.auth.SessionConst;
import kr.haedal.ondal.auth.oidc.OidcLoginException;
import kr.haedal.ondal.auth.oidc.PendingLogin;
import kr.haedal.ondal.auth.service.OidcAuthService;
import kr.haedal.ondal.auth.service.OidcAuthService.LoginResult;
import kr.haedal.ondal.auth.service.OidcAuthService.LoginStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 홈페이지(Keycloak) 로그인 API - ondal.auth.mode=oidc(prod) 에서만 뜬다.
 * 두 엔드포인트 모두 fetch 가 아니라 "브라우저가 직접 이동하는 주소"다 - 응답은 항상 302:
 *   GET /login    → 홈페이지 로그인 화면으로
 *   GET /callback → 성공: 세션 발급 후 FE(returnTo) 로 / 실패: FE 로그인 화면으로 (?error=OidcLoginError 이름)
 * 세션 처리(PendingLogin 보관·1회용 소비·로그인 세션 발급)는 여기서, 프로토콜(state·PKCE·토큰 교환·검증)은 OidcAuthService 에서.
 * 고위험 영역(인증) - PM 담당.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.OIDC)
public class OidcAuthController {

    private static final Logger log = LoggerFactory.getLogger(OidcAuthController.class);

    private final OidcAuthService oidcAuthService;

    public OidcAuthController(OidcAuthService oidcAuthService) {
        this.oidcAuthService = oidcAuthService;
    }

    /** 공개 경로(AuthPaths.PUBLIC). FE 의 "홈페이지 계정으로 로그인" 버튼이 이 주소로 이동한다 */
    @Operation(summary = "로그인 시작 - 홈페이지(Keycloak) 로그인 화면으로 302. returnTo: 로그인 후 돌아갈 FE 내부 경로(/로 시작, 그 외는 /). app: 어느 FE 로 돌아올지(설정에 등록된 키만, 예: hoj). 생략하면 기본 FE")
    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam(required = false) String returnTo,
                                      @RequestParam(required = false) String app,
                                      HttpServletRequest request) {
        try {
            LoginStart start = oidcAuthService.beginLogin(returnTo, app);
            // 콜백에서 대조할 값(state·nonce·code_verifier)은 세션에만 둔다 - 브라우저에 그대로 노출되는 URL 에는 state·nonce·challenge 만 나간다
            request.getSession(true).setAttribute(SessionConst.OIDC_PENDING_LOGIN, start.pending());
            return redirect(start.authorizationUri());
        } catch (OidcLoginException e) {
            log.warn("[auth] 로그인 시작 실패 {}: {}", e.error(), e.getMessage(), e.getCause());
            return redirect(oidcAuthService.loginErrorUri(e.error(), returnTo, app));
        }
    }

    /** 공개 경로(AuthPaths.PUBLIC). Keycloak 이 사용자 브라우저를 돌려보내는 주소(redirect_uri) */
    @Operation(summary = "로그인 콜백 - 인가 코드 교환·ID 토큰 검증 후 세션 발급, FE 로 302 (실패: FE /login?error=코드)")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest request) {
        PendingLogin pending = consumePendingLogin(request);
        String returnTo = pending == null ? null : pending.returnTo();
        // 어느 FE 에서 시작한 로그인인지 - 그 앱으로 돌려보내고, 로그아웃 때도 같은 앱으로 복귀시킨다
        String app = pending == null ? null : pending.app();
        try {
            LoginResult result = oidcAuthService.completeLogin(pending, code, state, error);
            HttpSession session = LoginSession.establish(request, result.user());
            session.setAttribute(SessionConst.OIDC_ID_TOKEN, result.idToken());
            session.setAttribute(SessionConst.OIDC_APP, app);
            return redirect(oidcAuthService.frontendUri(result.returnTo(), app));
        } catch (OidcLoginException e) {
            log.warn("[auth] 로그인 실패 {}: {}", e.error(), e.getMessage(), e.getCause());
            return redirect(oidcAuthService.loginErrorUri(e.error(), returnTo, app));
        }
    }

    /** 세션의 PendingLogin 을 꺼내면서 지운다 - 같은 콜백 URL 을 다시 열어도(뒤로 가기·새로고침) 두 번째부터는 STATE_MISMATCH */
    private static PendingLogin consumePendingLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(SessionConst.OIDC_PENDING_LOGIN);
        session.removeAttribute(SessionConst.OIDC_PENDING_LOGIN);
        return attribute instanceof PendingLogin pending ? pending : null;
    }

    private static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
