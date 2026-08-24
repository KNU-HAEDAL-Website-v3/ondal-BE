package kr.haedal.ondal.auth.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.haedal.ondal.auth.SessionConst;
import kr.haedal.ondal.common.error.ForbiddenException;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.UnauthenticatedException;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 권한 판정 공통 컴포넌트 - permissions.md 2절 "판정 순서" 4단계의 ②③④.
 * AuthInterceptor(①로그인 여부) 다음에 실행되며, 같은 경로(/api/**)·같은 제외 목록(AuthPaths.PUBLIC)을 쓴다.
 *
 * 핸들러의 "유효 어노테이션" 하나를 고른다 (AuthorizationAnnotations.resolve - 메서드에 있으면 그것, 없으면 클래스 것):
 *   @LoginOnly  → 통과
 *   @AdminOnly  → 전역 ADMIN 아니면 403
 *   @CohortRole → 경로 {cohortId} 분반에서 요구 역할 이상 아니면 403 (ADMIN은 통과)
 *   (없음)      → 500 - 붙이는 걸 잊은 것. 기동 시 검증에서 이미 막히지만 여기서도 fail-closed.
 *
 * 이 인터셉터는 Cohort를 로드하지 않고 HTTP 메서드도 보지 않는다. 보관 여부는 도메인 규칙(409)이지 권한이 아니다.
 * 고위험 영역 - PM 담당.
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    static final String COHORT_ID_VARIABLE = "cohortId";

    private final UserRepository userRepository;
    private final CohortAuthorizer cohortAuthorizer;

    public AuthorizationInterceptor(UserRepository userRepository, CohortAuthorizer cohortAuthorizer) {
        this.userRepository = userRepository;
        this.cohortAuthorizer = cohortAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight와 일반 OPTIONS(Spring이 Allow 헤더만 돌려주는 투명 처리)는 데이터가 나가지 않으므로 판정 대상이 아니다
        if (CorsUtils.isPreFlightRequest(request) || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true; // 정적 리소스, 에러 페이지 등 - 컨트롤러가 아니면 판정 대상이 아니다
        }

        Annotation effective = AuthorizationAnnotations.resolve(handlerMethod); // 없으면 IllegalStateException(500)

        // {cohortId}는 ADMIN 판정보다 먼저 읽는다 - ADMIN 세션으로만 테스트해도 경로 규약 위반·비숫자 id가 드러나도록
        Long cohortId = effective instanceof CohortRole ? readCohortId(request, handlerMethod) : null;

        User user = loadLoginUser(request);

        if (effective instanceof LoginOnly) {
            return true;
        }
        if (effective instanceof AdminOnly) {
            if (!user.isAdmin()) {
                throw new ForbiddenException();
            }
            return true;
        }
        CohortRole cohortRole = (CohortRole) effective;
        if (!cohortAuthorizer.isAllowed(user, cohortId, cohortRole.value())) {
            throw new ForbiddenException();
        }
        return true;
    }

    /**
     * URI 템플릿 변수는 컨트롤러의 @PathVariable 바인딩보다 먼저(String으로) 읽게 된다.
     * 그래서 숫자가 아닌 값(/api/cohorts/abc)의 400 처리는 여기서 직접 한다 - 핸들러 쪽 400 변환에는 도달하지 않기 때문.
     */
    private static Long readCohortId(HttpServletRequest request, HandlerMethod handlerMethod) {
        @SuppressWarnings("unchecked")
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String raw = variables == null ? null : variables.get(COHORT_ID_VARIABLE);
        if (raw == null) {
            throw new IllegalStateException("@CohortRole 핸들러의 경로에 {" + COHORT_ID_VARIABLE + "} 변수가 없다: " + handlerMethod);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("cohortId: 숫자여야 합니다.");
        }
    }

    private User loadLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId == null) {
            throw new UnauthenticatedException(); // AuthInterceptor가 먼저 걸렀겠지만 방어적으로
        }
        return userRepository.findById((Long) userId).orElseThrow(UnauthenticatedException::new);
    }
}
