package kr.haedal.hoj.auth.authorization;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.stream.Stream;

/**
 * 핸들러의 "유효 권한 어노테이션" 하나를 고르는 규칙 — 인터셉터와 기동 검증기가 같은 규칙을 쓴다.
 *
 * - 메서드에 하나라도 있으면 메서드 것, 없으면 클래스 것 (위치 우선). 클래스 @LoginOnly + 메서드 @AdminOnly 면 AdminOnly.
 * - 같은 위치(메서드 또는 클래스)에 3종 중 둘 이상이 있으면 설정 오류 → IllegalStateException (기동 검증에서 부팅 실패).
 * - 아무 데도 없으면 → IllegalStateException.
 * 종류별로 따로 찾아 "LoginOnly가 있으면 통과" 식으로 판정하면 클래스 LoginOnly가 메서드 AdminOnly를 덮어 권한이 새므로,
 * 반드시 이 클래스를 통해 하나만 고른다.
 */
final class AuthorizationAnnotations {

    static final List<Class<? extends Annotation>> TYPES = List.of(LoginOnly.class, AdminOnly.class, CohortRole.class);

    private AuthorizationAnnotations() {
    }

    /** 유효 어노테이션. 없거나 한 위치에 둘 이상이면 IllegalStateException. */
    static Annotation resolve(HandlerMethod handlerMethod) {
        List<Annotation> onMethod = findAll(handlerMethod.getMethod());
        if (!onMethod.isEmpty()) {
            return single(onMethod, "메서드 " + handlerMethod);
        }
        List<Annotation> onClass = findAll(handlerMethod.getBeanType());
        if (!onClass.isEmpty()) {
            return single(onClass, "클래스 " + handlerMethod.getBeanType().getSimpleName());
        }
        throw new IllegalStateException("권한 어노테이션이 없는 API: " + handlerMethod
                + " — @LoginOnly / @AdminOnly / @CohortRole 중 하나를 붙여야 한다.");
    }

    /** 검증기용 — 예외 대신 문제 설명을 돌려준다 (null 이면 정상) */
    static String violation(HandlerMethod handlerMethod) {
        List<Annotation> onMethod = findAll(handlerMethod.getMethod());
        if (onMethod.size() > 1) {
            return "메서드에 권한 어노테이션이 둘 이상 (" + names(onMethod) + ")";
        }
        List<Annotation> onClass = findAll(handlerMethod.getBeanType());
        if (onClass.size() > 1) {
            return "클래스에 권한 어노테이션이 둘 이상 (" + names(onClass) + ")";
        }
        if (onMethod.isEmpty() && onClass.isEmpty()) {
            return "권한 어노테이션 없음 (@LoginOnly / @AdminOnly / @CohortRole 중 하나 필요)";
        }
        return null;
    }

    private static Annotation single(List<Annotation> found, String where) {
        if (found.size() > 1) {
            throw new IllegalStateException(where + " 에 권한 어노테이션이 둘 이상 (" + names(found) + ") — 하나만 달아야 한다.");
        }
        return found.get(0);
    }

    private static List<Annotation> findAll(AnnotatedElement element) {
        return TYPES.stream()
                .map(type -> (Annotation) AnnotatedElementUtils.findMergedAnnotation(element, type))
                .flatMap(a -> a == null ? Stream.empty() : Stream.of(a))
                .toList();
    }

    private static String names(List<Annotation> annotations) {
        return String.join(", ", annotations.stream().map(a -> "@" + a.annotationType().getSimpleName()).toList());
    }
}
