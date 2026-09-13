package kr.haedal.ondal.auth.oidc;

import kr.haedal.ondal.auth.AuthMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** oidc 모드에서만 OidcProperties 를 바인딩·검증한다 - stub 모드(local·test)에서는 OIDC 설정이 없어도 기동해야 하므로 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.OIDC)
@EnableConfigurationProperties(OidcProperties.class)
class OidcConfig {
}
