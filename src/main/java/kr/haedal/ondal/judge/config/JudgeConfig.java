package kr.haedal.ondal.judge.config;

import kr.haedal.ondal.judge.engine.JudgeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 채점 슬라이스 설정 - 프로퍼티 바인딩, 채점 워커 스레드 풀, Judge0 HTTP 클라이언트 빌더.
 *
 * judgeExecutor: 제출 1건 = 작업 1개. 스레드 2개(엔진 워커 수와 맞춤), 큐는 무제한 - 폭주해도 제출은 성공하고 채점만 늦어진다 (design.md 결정 14).
 * ondal.judge.async=false(테스트) 면 호출 스레드에서 바로 실행 - 테스트가 제출 직후 결과를 단언할 수 있다.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(JudgeProperties.class)
public class JudgeConfig {

    public static final String EXECUTOR = "judgeExecutor";

    @Bean(EXECUTOR)
    public TaskExecutor judgeExecutor(JudgeProperties properties) {
        if (!properties.async()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("judge-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /** Judge0 호출용 - 연결 5초·응답 30초. 테스트는 MockRestServiceServer 를 묶은 빌더를 직접 넘긴다 */
    @Bean("judge0RestClientBuilder")
    public RestClient.Builder judge0RestClientBuilder() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
