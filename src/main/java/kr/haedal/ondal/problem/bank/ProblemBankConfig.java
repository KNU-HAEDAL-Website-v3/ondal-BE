package kr.haedal.ondal.problem.bank;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** ondal.problem-bank.* 바인딩 - 문제 은행 레포(GitHub)에서 직접 가져오기 (docs 결정 11 보완) */
@Configuration
@EnableConfigurationProperties(ProblemBankProperties.class)
public class ProblemBankConfig {
}
