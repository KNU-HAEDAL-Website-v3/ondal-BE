package kr.haedal.ondal.auth.authorization;

import kr.haedal.ondal.enrollment.entity.EnrollmentRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * "경로의 {cohortId} 분반에서 이 역할 이상인 사람만". 전역 ADMIN은 자동 통과. (docs: permissions.md 2절 4단계)
 *
 * 규약:
 * - 이 어노테이션을 쓰는 핸들러의 경로에는 반드시 {cohortId} 변수가 있어야 한다 (이름 고정). 기동 시 검증된다.
 * - 분반 스코프 리소스(수강생·과제·제출·현황판)는 항상 /api/cohorts/{cohortId}/... 아래에 둔다.
 * - 인터셉터가 보장하는 것은 "요청자가 {cohortId} 분반의 해당 역할"까지다.
 *   경로 뒤쪽의 하위 id(assignmentId 등)가 그 분반 것인지는 서비스가 findByIdAndCohortId 로 확인한다.
 *
 * 사용: @CohortRole(EnrollmentRole.OPERATOR) - 운영진 이상 / @CohortRole(EnrollmentRole.STUDENT) - 소속자 누구나
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CohortRole {

    EnrollmentRole value();
}
