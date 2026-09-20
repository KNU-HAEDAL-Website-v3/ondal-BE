package kr.haedal.ondal.hoj.service;

import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.hoj.dto.HojUserPageResponse;
import kr.haedal.ondal.hoj.dto.HojUserPageResponse.ActivityDay;
import kr.haedal.ondal.hoj.dto.HojUserPageResponse.ProblemBrief;
import kr.haedal.ondal.hoj.dto.HojUserPageResponse.Stats;
import kr.haedal.ondal.hoj.dto.HojUserPageResponse.TagStat;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.problem.dto.TagResponse;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.repository.ProblemRepository;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자 페이지 (docs hoj/api.md 3절) - 로그인한 누구나 누구의 페이지든. 이름·활동만 (loginId 없음).
 * 조립은 상수 회 쿼리(12번 안팎) - 푼 문제·시도 문제·태그·언어·잔디·최근 제출·순위를 각각 집계 쿼리 하나로 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class HojUserPageService {

    /** 잔디 기준 시간대 - 저장은 UTC, 날짜 경계는 한국 (CLAUDE.md 원칙 4) */
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int ACTIVITY_DAYS = 365;
    static final int RECENT_SUBMISSIONS = 20;

    private final UserRepository userRepository;
    private final JudgeResultRepository judgeResultRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final HojSubmissionFeedService feedService;
    private final HojRankingService rankingService;

    public HojUserPageService(UserRepository userRepository, JudgeResultRepository judgeResultRepository,
                              SubmissionRepository submissionRepository, ProblemRepository problemRepository,
                              HojSubmissionFeedService feedService, HojRankingService rankingService) {
        this.userRepository = userRepository;
        this.judgeResultRepository = judgeResultRepository;
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.feedService = feedService;
        this.rankingService = rankingService;
    }

    public HojUserPageResponse pageOf(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        // 푼 문제·시도 중 - 연습·과제 합산 (문제 목록의 solved 와 같은 규칙)
        Set<Long> solved = new HashSet<>(judgeResultRepository.findSolvedProblemIdsByUserId(userId));
        Set<Long> attempted = new HashSet<>(judgeResultRepository.findAttemptedProblemIdsByUserId(userId));
        attempted.removeAll(solved);
        Set<Long> touched = new HashSet<>(solved);
        touched.addAll(attempted);
        List<Problem> problems = touched.isEmpty() ? List.of() : problemRepository.findAllWithTagsByIdIn(touched);   // 번호순
        List<ProblemBrief> solvedProblems = problems.stream().filter(p -> solved.contains(p.getId())).map(ProblemBrief::from).toList();
        List<ProblemBrief> attemptedProblems = problems.stream().filter(p -> attempted.contains(p.getId())).map(ProblemBrief::from).toList();

        // 태그 숙련도 - 분모는 태그별 전체 문제 수, 분자는 푼 문제의 태그를 센다
        Map<Long, Integer> solvedPerTag = new HashMap<>();
        problems.stream()
                .filter(p -> solved.contains(p.getId()))
                .forEach(p -> p.getTags().forEach(tag -> solvedPerTag.merge(tag.getId(), 1, Integer::sum)));
        List<TagStat> tagStats = problemRepository.countGroupedByTag().stream()
                .map(t -> new TagStat(new TagResponse(t.tagId(), t.name()), solvedPerTag.getOrDefault(t.tagId(), 0), t.count().intValue()))
                .toList();

        // 제출 계열 - 연습 제출만
        long submissionCount = submissionRepository.countByUserIdAndProblemIsNotNull(userId);
        long acceptedCount = judgeResultRepository.countPracticeAcceptedByUserId(userId);
        Integer acceptedRate = submissionCount == 0 ? null : (int) (acceptedCount * 100 / submissionCount);

        Instant since = LocalDate.now(KST).minusDays(ACTIVITY_DAYS - 1).atStartOfDay(KST).toInstant();   // 오늘 포함 365일
        List<ActivityDay> activity = submissionRepository.countPracticeGroupedByKstDay(userId, since).stream()
                .map(row -> new ActivityDay((String) row[0], ((Number) row[1]).intValue()))
                .toList();

        return new HojUserPageResponse(
                UserSummary.of(user, null),   // 분반 문맥이 없다 - 직책은 전역 역할로만
                user.getCreatedAt(),
                rankingService.rankOf(userId),
                new Stats(solved.size(), attempted.size(), submissionCount, acceptedCount, acceptedRate),
                submissionRepository.countPracticeGroupedByLanguage(userId),
                solvedProblems,
                attemptedProblems,
                tagStats,
                activity,
                feedService.recentOf(userId, RECENT_SUBMISSIONS));
    }
}
