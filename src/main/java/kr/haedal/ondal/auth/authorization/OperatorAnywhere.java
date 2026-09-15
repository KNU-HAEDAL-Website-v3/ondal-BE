package kr.haedal.ondal.auth.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 전역 ADMIN 이거나, 어느 한 분반에서라도 교육운영진인 사람 - 문제 라이브러리 출제·수정용 (docs permissions.md).
 *
 * 문제(Problem)는 분반에 속하지 않아 경로에 {cohortId} 가 없다. 그래서 @CohortRole 로는 판정할 수 없다.
 * "출제는 극소수가 한다"(2026-09-15 PM)는 전제 위에서, 운영진이면 어느 반 소속이든 문제를 만들 수 있게 한다.
 * 태그 어휘 관리는 이보다 좁게 @AdminOnly - 표기가 갈라지면 분류가 쓸모없어지기 때문.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperatorAnywhere {
}
