package kr.haedal.ondal.auth.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * "로그인만 되어 있으면 누구나" - 분반 권한과 무관한 API에 붙인다. (예: GET /api/me/cohorts, GET /api/auth/me)
 *
 * /api/** 의 모든 핸들러는 @LoginOnly · @AdminOnly · @CohortRole 중 하나를 반드시 단다.
 * 하나도 없으면 기동 시 AuthorizationMappingValidator가 부팅을 막고,
 * 어떤 이유로 기동 검증을 못 거쳤더라도 AuthorizationInterceptor가 500으로 막는다(fail-closed).
 * "권한 어노테이션을 잊어서 데이터가 새는" 사고를 구조적으로 없애기 위한 장치다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginOnly {
}
