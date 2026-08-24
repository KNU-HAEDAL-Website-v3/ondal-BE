package kr.haedal.ondal.auth.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 전역 ADMIN(동아리 임원)만 - 분반 생성·보관, 운영진 지정 등. (docs: permissions.md 3절, 4절)
 * 경로에 {cohortId}가 있어도 분반 소속은 보지 않는다 (ADMIN은 모든 분반에서 운영자 이상).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {
}
