package kr.haedal.hoj.common.config;

import kr.haedal.hoj.user.User;
import kr.haedal.hoj.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local 프로필 전용 부트스트랩 — permissions.md §4 "최초 관리자"의 개발 환경 버전.
 * 운영 배포에서는 이 시더가 돌지 않으며, 실제 계정에 수동 SQL로 ADMIN을 지정한다.
 */
@Component
@Profile("local")
public class LocalDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

    private final UserRepository userRepository;

    public LocalDataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByLoginId("admin").isEmpty()) {
            userRepository.save(User.admin("admin", "관리자"));
            log.info("[seed] local 관리자 계정 생성: loginId=admin");
        }
    }
}
