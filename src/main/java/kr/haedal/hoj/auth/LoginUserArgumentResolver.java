package kr.haedal.hoj.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.haedal.hoj.common.error.UnauthenticatedException;
import kr.haedal.hoj.user.User;
import kr.haedal.hoj.user.UserRepository;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** @LoginUser 파라미터에 세션의 사용자 id로 조회한 최신 User를 넣어준다. */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    public LoginUserArgumentResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new UnauthenticatedException();
        }
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId == null) {
            throw new UnauthenticatedException();
        }
        // 세션은 살아있는데 사용자가 DB에서 지워진 경우도 401로 처리
        return userRepository.findById((Long) userId)
                .orElseThrow(UnauthenticatedException::new);
    }
}
