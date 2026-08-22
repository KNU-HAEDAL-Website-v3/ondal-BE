package kr.haedal.hoj.common.config;

import kr.haedal.hoj.cohort.entity.Cohort;
import kr.haedal.hoj.cohort.repository.CohortRepository;
import kr.haedal.hoj.enrollment.entity.Enrollment;
import kr.haedal.hoj.enrollment.repository.EnrollmentRepository;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.user.entity.User;
import kr.haedal.hoj.user.repository.UserRepository;
import kr.haedal.hoj.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * local 프로필 전용 부트스트랩 — permissions.md §4 "최초 관리자"의 개발 환경 버전 + 프론트 개발용 샘플 분반.
 * 운영 배포에서는 이 시더가 돌지 않으며, 실제 계정에 수동 SQL로 ADMIN을 지정한다.
 * 테스트(@ActiveProfiles("test"))에서도 돌지 않는다 — 테스트 픽스처는 각 테스트가 직접 만든다.
 *
 * 만드는 계정: admin(ADMIN) / operator1 / student1, student2, student3 (전부 스텁 로그인으로 바로 로그인 가능)
 * 만드는 분반: "2026-2 C언어"(ACTIVE: operator1 + student1~3), "2026-1 파이썬"(ARCHIVED: student1)
 */
@Component
@Profile("local")
public class LocalDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;

    public LocalDataSeeder(UserRepository userRepository,
                           UserService userService,
                           CohortRepository cohortRepository,
                           EnrollmentRepository enrollmentRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByLoginId("admin").isEmpty()) {
            userRepository.save(User.admin("admin", "관리자"));
            log.info("[seed] local 관리자 계정 생성: loginId=admin");
        }
        if (cohortRepository.count() > 0) {
            return; // 이미 분반이 있으면 샘플을 다시 만들지 않는다
        }

        Cohort current = cohortRepository.save(Cohort.create("2026-2 C언어", "샘플 분반 (진행 중)"));
        enroll(current, "operator1", EnrollmentRole.OPERATOR);
        for (String loginId : List.of("student1", "student2", "student3")) {
            enroll(current, loginId, EnrollmentRole.STUDENT);
        }

        Cohort past = Cohort.create("2026-1 파이썬", "샘플 분반 (보관됨)");
        past.archive();
        cohortRepository.save(past);
        enroll(past, "student1", EnrollmentRole.STUDENT);

        log.info("[seed] 샘플 분반 생성: '{}'(ACTIVE), '{}'(ARCHIVED). 계정: operator1, student1~3",
                current.getName(), past.getName());
    }

    private void enroll(Cohort cohort, String loginId, EnrollmentRole role) {
        User user = userService.findOrCreateMember(loginId);
        enrollmentRepository.save(Enrollment.create(cohort, user, role));
    }
}
