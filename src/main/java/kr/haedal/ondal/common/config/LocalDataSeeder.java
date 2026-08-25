package kr.haedal.ondal.common.config;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;
import kr.haedal.ondal.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * local 프로필 전용 부트스트랩 - permissions.md 4절 "최초 관리자"의 개발 환경 버전 + 프론트 개발용 샘플 분반.
 * 운영 배포에서는 이 시더가 돌지 않으며, 실제 계정에 수동 SQL로 ADMIN을 지정한다.
 * 테스트(@ActiveProfiles("test"))에서도 돌지 않는다 - 테스트 픽스처는 각 테스트가 직접 만든다.
 *
 * 만드는 계정: admin(ADMIN) / operator1 / student1, student2, student3 (전부 스텁 로그인으로 바로 로그인 가능)
 * 만드는 분반: "2026-2 C언어"(ACTIVE: operator1 + student1~3), "2026-1 파이썬"(ARCHIVED: student1)
 * 만드는 과제: 진행 중 분반에 3개 - 1차시(마감 지남) · 2차시(마감 전) · 차시 없음 (FE가 그룹핑·정렬·D-day까지 바로 확인)
 */
@Component
@Profile("local")
public class LocalDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;

    public LocalDataSeeder(UserRepository userRepository,
                           UserService userService,
                           CohortRepository cohortRepository,
                           EnrollmentRepository enrollmentRepository,
                           AssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
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

        Instant now = Instant.now();
        assignmentRepository.save(Assignment.create(current, 1, "1차시 - 입출력 연습",
                "백준 1000번(A+B)을 풀고 코드를 제출하세요. https://www.acmicpc.net/problem/1000",
                now.minus(3, ChronoUnit.DAYS)));
        assignmentRepository.save(Assignment.create(current, 2, "2차시 - 조건문과 반복문",
                "백준 2739번(구구단), 9498번(시험 성적)을 풀어 제출하세요.",
                now.plus(7, ChronoUnit.DAYS)));
        assignmentRepository.save(Assignment.create(current, null, "설문 - 스터디 시간 조사",
                "차시와 무관한 공지형 과제입니다. 설문 링크를 확인하세요.",
                now.plus(14, ChronoUnit.DAYS)));

        log.info("[seed] 샘플 분반 생성: '{}'(ACTIVE, 과제 3개), '{}'(ARCHIVED). 계정: operator1, student1~3",
                current.getName(), past.getName());
    }

    private void enroll(Cohort cohort, String loginId, EnrollmentRole role) {
        User user = userService.findOrCreateMember(loginId);
        enrollmentRepository.save(Enrollment.create(cohort, user, role));
    }
}
