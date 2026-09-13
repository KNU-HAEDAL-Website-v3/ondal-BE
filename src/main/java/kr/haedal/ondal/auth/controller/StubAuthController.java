package kr.haedal.ondal.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.AuthMode;
import kr.haedal.ondal.auth.LoginSession;
import kr.haedal.ondal.auth.dto.LoginRequest;
import kr.haedal.ondal.auth.service.StubAuthService;
import kr.haedal.ondal.user.dto.UserResponse;
import kr.haedal.ondal.user.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스텁 로그인 API - ondal.auth.mode=stub(local·test) 에서만 뜬다. 운영(oidc)에는 이 엔드포인트 자체가 없다(POST /api/auth/login → 405).
 * 테스트 픽스처(LoginHelper)는 이 API 를 부르지 않고 세션 속성을 직접 넣으므로 모드와 무관하다.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.STUB, matchIfMissing = true)
public class StubAuthController {

    private final StubAuthService stubAuthService;

    public StubAuthController(StubAuthService stubAuthService) {
        this.stubAuthService = stubAuthService;
    }

    /** 공개 경로(AuthPaths.PUBLIC) - 권한 어노테이션 없음 */
    @Operation(summary = "로그인 (스텁: loginId만 보내면 통과, 없으면 MEMBER로 생성) - local·test 전용, 운영에는 없음")
    @PostMapping("/login")
    public UserResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest httpRequest) {
        User user = stubAuthService.login(request.loginId());
        LoginSession.establish(httpRequest, user);
        // 연관관계 없는 단일 엔티티라 컨트롤러에서 바로 DTO로 바꾼다 - auth만의 예외 (docs: cohort/design.md 4절)
        return UserResponse.from(user);
    }
}
