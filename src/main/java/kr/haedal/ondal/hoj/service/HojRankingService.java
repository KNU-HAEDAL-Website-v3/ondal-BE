package kr.haedal.ondal.hoj.service;

import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.hoj.dto.HojRankingResponse;
import kr.haedal.ondal.judge.dto.SolvedProblemRow;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.submission.dto.UserSubmissionCount;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 랭킹 (docs hoj/api.md 4절) - 푼 문제 수 하나로 줄 세운다. 점수·티어를 두면 채점 기준 논쟁이 생긴다 (PM 결정 13).
 *
 * 순위 기준: solvedCount desc → lastSolvedAt asc(같은 수면 먼저 도달한 사람) → name asc. 푼 문제 수가 같으면 같은 순위(1, 1, 3) -
 * 시각·이름은 표시 순서만 정한다. 푼 문제 0개는 목록에 없다.
 * 원자료는 (사용자, 문제)별 첫 정답 시각 행 전부(쿼리 1번) - 동아리 규모(수백 명 × 수백 문제)라 메모리에서 세는 편이 파생 테이블 SQL 보다 단순하다.
 */
@Service
@Transactional(readOnly = true)
public class HojRankingService {

    public static final int DEFAULT_SIZE = 100;
    public static final int MAX_SIZE = 200;

    private final JudgeResultRepository judgeResultRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CohortRepository cohortRepository;

    public HojRankingService(JudgeResultRepository judgeResultRepository, SubmissionRepository submissionRepository,
                             UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                             CohortRepository cohortRepository) {
        this.judgeResultRepository = judgeResultRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cohortRepository = cohortRepository;
    }

    /** cohortId 를 주면 그 분반 소속(수강생·운영진)만으로 다시 줄 세운다. me 는 요청자 - 목록에 없으면(0개) null */
    public HojRankingResponse ranking(Long cohortId, Integer size, User me) {
        int limit = size == null ? DEFAULT_SIZE : Math.max(1, Math.min(size, MAX_SIZE));
        Set<Long> only = null;
        if (cohortId != null) {
            if (!cohortRepository.existsById(cohortId)) {
                throw new NotFoundException("분반을 찾을 수 없습니다.");
            }
            only = enrollmentRepository.findAllByCohortIdWithUser(cohortId).stream()
                    .map(enrollment -> enrollment.getUser().getId())
                    .collect(Collectors.toSet());
        }
        List<Ranked> ranked = rank(only);
        HojRankingResponse.Me meEntry = ranked.stream()
                .filter(r -> r.user().getId().equals(me.getId()))
                .findFirst()
                .map(r -> new HojRankingResponse.Me(r.rank(), r.solvedCount()))
                .orElse(null);

        List<Ranked> top = ranked.size() > limit ? ranked.subList(0, limit) : ranked;
        Map<Long, Long> submissionCounts = top.isEmpty() ? Map.of()
                : submissionRepository.countPracticeGroupedByUserIdIn(top.stream().map(r -> r.user().getId()).toList()).stream()
                        .collect(Collectors.toMap(UserSubmissionCount::userId, UserSubmissionCount::count));
        List<HojRankingResponse.Entry> items = top.stream()
                .map(r -> new HojRankingResponse.Entry(r.rank(), UserSummary.of(r.user(), null),   // HOJ 는 분반 문맥이 없다
                        r.solvedCount(), submissionCounts.getOrDefault(r.user().getId(), 0L), r.lastSolvedAt()))
                .toList();
        return new HojRankingResponse(items, meEntry);
    }

    /** 사용자 페이지의 rank - 전체 랭킹에서의 순위. 푼 문제 0개면 null */
    public Integer rankOf(Long userId) {
        return rank(null).stream()
                .filter(r -> r.user().getId().equals(userId))
                .findFirst()
                .map(Ranked::rank)
                .orElse(null);
    }

    /** only 가 null 이면 전체, 아니면 그 사용자들만. 쿼리 2번(정답 행 + 사용자) */
    private List<Ranked> rank(Set<Long> only) {
        Map<Long, Tally> tallies = new HashMap<>();
        for (SolvedProblemRow row : judgeResultRepository.findSolvedProblemRows()) {
            if (only != null && !only.contains(row.userId())) {
                continue;
            }
            tallies.computeIfAbsent(row.userId(), k -> new Tally()).add(row.firstAcceptedAt());
        }
        if (tallies.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userRepository.findAllById(tallies.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Ranked> sorted = tallies.entrySet().stream()
                .filter(entry -> users.containsKey(entry.getKey()))
                .map(entry -> new Ranked(0, users.get(entry.getKey()), entry.getValue().count, entry.getValue().lastSolvedAt))
                .sorted(Comparator.comparingInt(Ranked::solvedCount).reversed()
                        .thenComparing(Ranked::lastSolvedAt)
                        .thenComparing(r -> r.user().getName()))
                .toList();

        List<Ranked> result = new ArrayList<>(sorted.size());
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Ranked current = sorted.get(i);
            if (i == 0 || current.solvedCount() != sorted.get(i - 1).solvedCount()) {
                rank = i + 1;   // 동점 다음은 건너뛴 번호 (1, 1, 3)
            }
            result.add(current.withRank(rank));
        }
        return result;
    }

    /** 사용자 하나의 집계 - 푼 문제 수와 그 수에 도달한 시각(문제별 첫 정답 시각의 최댓값) */
    private static final class Tally {
        private int count;
        private Instant lastSolvedAt;

        void add(Instant firstAcceptedAt) {
            count++;
            if (lastSolvedAt == null || firstAcceptedAt.isAfter(lastSolvedAt)) {
                lastSolvedAt = firstAcceptedAt;
            }
        }
    }

    private record Ranked(int rank, User user, int solvedCount, Instant lastSolvedAt) {
        Ranked withRank(int value) {
            return new Ranked(value, user, solvedCount, lastSolvedAt);
        }
    }
}
