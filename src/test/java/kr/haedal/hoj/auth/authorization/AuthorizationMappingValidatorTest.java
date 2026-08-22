package kr.haedal.hoj.auth.authorization;

import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 기동 시 검증 규칙의 단위 테스트 - 스프링 컨텍스트 없이 가짜 매핑으로 validate() 를 직접 호출한다.
 * (실제 컨텍스트에서 위반이 있으면 다른 모든 @SpringBootTest 가 기동 실패로 함께 알려준다)
 */
class AuthorizationMappingValidatorTest {

    /** 테스트용 가짜 컨트롤러 - 메서드 이름이 곧 시나리오. 패키지가 kr.haedal.hoj 라 규칙 (e) 대상이다 */
    static class FakeController {
        @LoginOnly public void loginOnlyOk() {}
        @AdminOnly public void adminOnlyOk() {}
        @CohortRole(EnrollmentRole.STUDENT) public void cohortRoleOk() {}
        public void noAnnotation() {}
        @CohortRole(EnrollmentRole.OPERATOR) public void cohortRoleWithoutPathVar() {}
        @LoginOnly public void loginOnlyOnCohortPath() {}
        @LoginOnly @AdminOnly public void twoAnnotations() {}
        public void publicLogin() {}
    }

    /** 클래스 레벨 @LoginOnly - 메서드에 있으면 메서드가 이긴다 */
    @LoginOnly
    static class ClassLevelController {
        public void inheritsLoginOnly() {}
        @AdminOnly public void adminOverridesClass() {}
        @CohortRole(EnrollmentRole.OPERATOR) public void cohortRoleOverridesClass() {}
    }

    @Test
    void 규약을_지킨_매핑은_위반이_없다() throws Exception {
        List<String> violations = AuthorizationMappingValidator.validate(Map.of(
                info("/api/me/cohorts"), handler("loginOnlyOk"),
                info("/api/cohorts"), handler("adminOnlyOk"),
                info("/api/cohorts/{cohortId}"), handler("cohortRoleOk"),
                info("/api/auth/login"), handler("publicLogin"),        // 공개 경로 - 어노테이션 없어도 OK
                info("/api/things"), classLevel("inheritsLoginOnly"),   // 클래스 @LoginOnly 상속
                info("/api/cohorts/{cohortId}/x"), classLevel("adminOverridesClass"),      // 메서드 @AdminOnly 가 이김 → (c) 충족
                info("/api/cohorts/{cohortId}/y"), classLevel("cohortRoleOverridesClass")  // 메서드 @CohortRole 이 이김
        ));
        assertThat(violations).isEmpty();
    }

    @Test
    void 어노테이션_없는_api_핸들러는_위반() throws Exception {
        List<String> violations = AuthorizationMappingValidator.validate(Map.of(
                info("/api/things"), handler("noAnnotation")));
        assertThat(violations).singleElement().asString().contains("noAnnotation").contains("권한 어노테이션 없음");
    }

    @Test
    void 한_메서드에_어노테이션이_둘이면_위반() throws Exception {
        List<String> violations = AuthorizationMappingValidator.validate(Map.of(
                info("/api/things"), handler("twoAnnotations")));
        assertThat(violations).singleElement().asString().contains("둘 이상");
    }

    @Test
    void CohortRole인데_경로에_cohortId가_없으면_위반() throws Exception {
        List<String> violations = AuthorizationMappingValidator.validate(Map.of(
                info("/api/things/{id}"), handler("cohortRoleWithoutPathVar")));
        assertThat(violations).singleElement().asString().contains("{cohortId}");
    }

    @Test
    void 경로에_cohortId가_있는데_유효_어노테이션이_LoginOnly면_위반() throws Exception {
        List<String> violations = AuthorizationMappingValidator.validate(Map.of(
                info("/api/cohorts/{cohortId}/things"), handler("loginOnlyOnCohortPath"),
                info("/api/cohorts/{cohortId}/z"), classLevel("inheritsLoginOnly")));   // 클래스 LoginOnly 만 있는 경우도 위반
        assertThat(violations).hasSize(2).allSatisfy(v -> assertThat(v).contains("@CohortRole / @AdminOnly 가 아님"));
    }

    @Test
    void 우리_컨트롤러가_api_밖에_매핑되면_위반() throws Exception {
        List<String> violations = AuthorizationMappingValidator.validate(Map.of(
                info("/apo/cohorts"), handler("adminOnlyOk")));   // prefix 오타
        assertThat(violations).singleElement().asString().contains("/api/ 아래에만");
    }

    @Test
    void 위반이_있으면_기동을_중단한다() throws Exception {
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandlerMethods()).thenReturn(Map.of(info("/api/things"), handler("noAnnotation")));

        assertThatThrownBy(() -> new AuthorizationMappingValidator(mapping).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("권한 규약 위반");
    }

    @Test
    void 인터셉터가_쓰는_유효_어노테이션도_메서드가_클래스보다_우선한다() throws Exception {
        assertThat(AuthorizationAnnotations.resolve(classLevel("adminOverridesClass"))).isInstanceOf(AdminOnly.class);
        assertThat(AuthorizationAnnotations.resolve(classLevel("inheritsLoginOnly"))).isInstanceOf(LoginOnly.class);
        assertThatThrownBy(() -> AuthorizationAnnotations.resolve(handler("twoAnnotations")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AuthorizationAnnotations.resolve(handler("noAnnotation")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RequestMappingInfo info(String path) {
        return RequestMappingInfo.paths(path).build();
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = FakeController.class.getMethod(methodName);
        return new HandlerMethod(new FakeController(), method);
    }

    private static HandlerMethod classLevel(String methodName) throws NoSuchMethodException {
        Method method = ClassLevelController.class.getMethod(methodName);
        return new HandlerMethod(new ClassLevelController(), method);
    }
}
