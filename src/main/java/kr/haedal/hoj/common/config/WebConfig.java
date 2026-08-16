package kr.haedal.hoj.common.config;

import kr.haedal.hoj.auth.AuthInterceptor;
import kr.haedal.hoj.auth.LoginUserArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final String[] allowedOrigins;

    public WebConfig(AuthInterceptor authInterceptor,
                     LoginUserArgumentResolver loginUserArgumentResolver,
                     @Value("${hoj.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.loginUserArgumentResolver = loginUserArgumentResolver;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 개발 중엔 프론트(예: localhost:5173)와 백엔드(8080)의 출처가 달라서 CORS 허용이 필요하다.
     * allowCredentials(true)가 핵심 — 세션 쿠키가 오가려면 자격증명 허용 + 와일드카드가 아닌 명시적 origin이어야 한다.
     * 운영에서는 리버스 프록시로 같은 도메인 아래에 두면 CORS 자체가 필요 없어진다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",   // 로그인 전이니 당연히 면제
                        "/api/auth/logout",  // 만료된 세션으로 눌러도 조용히 성공해야 함
                        "/api/health"        // 모니터링용
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
