package kr.haedal.ondal.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 현재 로그인한 User가 주입된다.
 * 사용: public XxxResponse xxx(@LoginUser User me) { ... }
 * 컨트롤러가 HttpSession을 직접 만지지 않게 하는 장치 - 주니어가 복제할 컨트롤러 패턴을 균일하게 유지한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
