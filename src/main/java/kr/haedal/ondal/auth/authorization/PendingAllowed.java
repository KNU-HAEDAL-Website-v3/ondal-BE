package kr.haedal.ondal.auth.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 승인 대기(UserStatus.PENDING) 계정도 부를 수 있는 API 표시 - 권한 어노테이션(@LoginOnly 등)에 **덧붙이는** 마커.
 *
 * 대기 계정은 원칙적으로 아무 API 도 못 쓴다(403 USER_PENDING). 예외는 "내가 지금 대기 중"임을 알아야 하는 자리뿐이다:
 *   - GET /api/auth/me : FE 가 대기 화면을 그리려면 status 를 읽어야 한다
 *   - (로그아웃·로그인은 공개 경로라 이 마커가 필요 없다)
 * 권한 판정 3종과 달리 하나만 골라지는 대상이 아니므로 AuthorizationAnnotations.TYPES 에 넣지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PendingAllowed {
}
