package kr.haedal.hoj.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.haedal.hoj.common.error.UnauthenticatedException;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/** 전체를 기본 잠금(default-deny)으로 지킨다.
 * 예외 경로는 WebConfig의 excludePathPatterns에만 나열한다 -
 * 새 API를 추가하면 아무 설정 없이도 자동으로 로그인 필수가 되도록.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 사전 요청(OPTIONS)은 브라우저가 보내는 확인 절차 - 세션이 없으니 통과시켜야 한다
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionConst.LOGIN_USER_ID) == null) {
            throw new UnauthenticatedException();
        }
        return true;
    }
}
