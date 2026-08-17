package kr.haedal.hoj.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL — Testcontainers 2.x (패키지 org.testcontainers.postgresql, 1.x의 containers.* 아님).
 * @ServiceConnection 이 datasource url/username/password 를 자동으로 채워주므로 application-test.yml 이 필요 없다.
 * 이미지 버전은 docker-compose.yml 의 PostgreSQL 과 맞춘다. 컨텍스트가 캐시되므로 컨테이너는 테스트 전체에서 1번만 뜬다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16");
    }
}
