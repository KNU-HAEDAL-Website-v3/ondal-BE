package kr.haedal.ondal.hoj.service;

import kr.haedal.ondal.hoj.dto.HojSubmissionFeedResponse;
import kr.haedal.ondal.hoj.dto.HojSubmissionItem;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.Verdict;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 채점 현황 피드 (docs hoj/api.md 2절) - **HOJ 연습 제출만**, 분반 과제 제출은 섞지 않는다 (PM 결정 13).
 *
 * 커서 방식(id desc, beforeId): 채점 중 5초마다 다시 부르는 화면이라 offset 페이징은 새 제출이 끼어들 때마다 줄이 밀린다.
 * 한 페이지 = 쿼리 2번 (제출 + 그 채점 결과) - 결과 행 수와 무관.
 */
@Service
@Transactional(readOnly = true)
public class HojSubmissionFeedService {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private final SubmissionRepository submissionRepository;
    private final JudgeResultRepository judgeResultRepository;

    public HojSubmissionFeedService(SubmissionRepository submissionRepository, JudgeResultRepository judgeResultRepository) {
        this.submissionRepository = submissionRepository;
        this.judgeResultRepository = judgeResultRepository;
    }

    /** 필터는 null 이면 무시. size 는 1~200 으로 자른다(기본 50). 한 건 더 읽어 "더 있는지"를 알아낸다 */
    public HojSubmissionFeedResponse feed(Long problemId, Long userId, Verdict verdict, String language, Integer size, Long beforeId) {
        int pageSize = size == null ? DEFAULT_SIZE : Math.max(1, Math.min(size, MAX_SIZE));
        String languageFilter = language == null || language.isBlank() ? null : language.strip();
        List<Submission> found = submissionRepository.findPracticeFeed(
                problemId, userId, languageFilter, verdict, beforeId, PageRequest.of(0, pageSize + 1));
        boolean hasMore = found.size() > pageSize;
        List<Submission> page = hasMore ? found.subList(0, pageSize) : found;
        return new HojSubmissionFeedResponse(toItems(page), hasMore ? page.get(page.size() - 1).getId() : null);
    }

    /** 사용자 페이지의 최근 제출 - 같은 행 모양, 최신 limit 건 */
    public List<HojSubmissionItem> recentOf(Long userId, int limit) {
        return toItems(submissionRepository.findPracticeFeed(null, userId, null, null, null, PageRequest.of(0, limit)));
    }

    private List<HojSubmissionItem> toItems(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }
        Map<Long, JudgeResult> judges = judgeResultRepository
                .findAllBySubmissionIdIn(submissions.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(JudgeResult::getSubmissionId, Function.identity()));
        return submissions.stream()
                .map(submission -> HojSubmissionItem.of(submission, judges.get(submission.getId())))
                .toList();
    }
}
