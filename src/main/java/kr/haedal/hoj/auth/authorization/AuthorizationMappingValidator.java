package kr.haedal.hoj.auth.authorization;

import kr.haedal.hoj.auth.AuthPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기동 시 검증 — 모든 API 핸들러가 권한 규약을 지키는지 부팅 단계에서 확인하고, 아니면 부팅을 중단한다.
 *
 * 규칙:
 *  (a) AuthPaths.PUBLIC이 아닌 /api/** 핸들러에는 @LoginOnly / @AdminOnly / @CohortRole 중 하나가 있어야 한다
 *  (b) @CohortRole 핸들러의 경로에는 {cohortId} 변수가 있어야 한다
 *  (c) 경로에 {cohortId}가 있으면 유효 어노테이션이 @CohortRole 또는 @AdminOnly 여야 한다 (@LoginOnly로 분반 자원을 열면 안 된다)
 *  (d) 한 위치(메서드 또는 클래스)에 3종 중 둘 이상을 달면 안 된다 — 어느 것이 이기는지 애매해지므로
 *  (e) 우리 패키지(kr.haedal.hoj)의 컨트롤러는 /api/ 아래에만 매핑한다 — prefix 오타(/apo/...)로 인터셉터를 통째로 비껴가는 것을 막는다
 *
 * "어노테이션 붙이는 걸 잊는" 실수가 컴파일 다음 단계에서, 요청이 오기 전에 잡히게 하는 장치. (docs: cohort/design.md §2)
 * 유효 어노테이션을 고르는 규칙 자체는 AuthorizationAnnotations 에 있고 인터셉터와 공유한다.
 */
@Component
public class AuthorizationMappingValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationMappingValidator.class);
    private static final String API_PREFIX = "/api/";
    private static final String OWN_PACKAGE = "kr.haedal.hoj";
    private static final String COHORT_ID_TOKEN = "{" + AuthorizationInterceptor.COHORT_ID_VARIABLE + "}";

    private final RequestMappingHandlerMapping handlerMapping;

    public AuthorizationMappingValidator(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = validate(handlerMapping.getHandlerMethods());
        if (!violations.isEmpty()) {
            throw new IllegalStateException("권한 규약 위반 API가 있어 기동을 중단합니다:\n - "
                    + String.join("\n - ", violations));
        }
        log.info("[authz] API 핸들러 권한 어노테이션 검증 통과");
    }

    /** 순수 함수 — 테스트에서 가짜 매핑으로 직접 호출한다 */
    static List<String> validate(Map<RequestMappingInfo, HandlerMethod> mappings) {
        List<String> violations = new ArrayList<>();
        Set<String> publicPaths = Set.of(AuthPaths.PUBLIC);

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.entrySet()) {
            HandlerMethod handler = entry.getValue();
            Set<String> patterns = entry.getKey().getPatternValues();
            List<String> apiPatterns = patterns.stream().filter(p -> p.startsWith(API_PREFIX)).toList();

            // (e) 우리 컨트롤러가 /api 밖에 매핑되어 있으면 인터셉터 2개를 모두 비껴간다
            if (handler.getBeanType().getPackageName().startsWith(OWN_PACKAGE)) {
                List<String> outside = patterns.stream().filter(p -> !p.startsWith(API_PREFIX)).toList();
                if (!outside.isEmpty()) {
                    violations.add(describe(handler, outside) + " : 우리 컨트롤러는 /api/ 아래에만 매핑한다");
                }
            }
            if (apiPatterns.isEmpty()) {
                continue; // /swagger-ui, /v3/api-docs, /error 등 — 대상 아님
            }
            if (publicPaths.containsAll(apiPatterns)) {
                continue; // 공개 경로 — 어노테이션 없음이 정상
            }

            String where = describe(handler, apiPatterns);

            // (a)(d) 유효 어노테이션이 정확히 하나로 정해지는가
            String problem = AuthorizationAnnotations.violation(handler);
            if (problem != null) {
                violations.add(where + " : " + problem);
                continue; // 유효 어노테이션이 없으니 아래 규칙은 판정 불가
            }
            Annotation effective = AuthorizationAnnotations.resolve(handler);
            boolean pathHasCohortId = apiPatterns.stream().anyMatch(p -> p.contains(COHORT_ID_TOKEN));
            boolean allPathsHaveCohortId = apiPatterns.stream().allMatch(p -> p.contains(COHORT_ID_TOKEN));

            // (b)
            if (effective instanceof CohortRole && !allPathsHaveCohortId) {
                violations.add(where + " : @CohortRole 인데 경로에 " + COHORT_ID_TOKEN + " 변수가 없음");
            }
            // (c)
            if (pathHasCohortId && !(effective instanceof CohortRole) && !(effective instanceof AdminOnly)) {
                violations.add(where + " : 경로에 " + COHORT_ID_TOKEN + " 가 있는데 유효 어노테이션이 @CohortRole / @AdminOnly 가 아님");
            }
        }
        return violations;
    }

    private static String describe(HandlerMethod handler, List<String> patterns) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName()
                + " " + Arrays.toString(patterns.toArray());
    }
}
