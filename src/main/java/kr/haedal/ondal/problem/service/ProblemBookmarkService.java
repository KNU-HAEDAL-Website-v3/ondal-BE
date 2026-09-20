package kr.haedal.ondal.problem.service;

import kr.haedal.ondal.problem.entity.ProblemBookmark;
import kr.haedal.ondal.problem.repository.ProblemBookmarkRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 북마크 - 로그인한 누구나 자기 것만, PUT/DELETE 멱등 (V11, docs hoj/api.md 7절).
 * 읽기는 별도 GET 없이 목록·상세의 bookmarked(ProblemService) 로 - 화면이 이미 문제 목록을 들고 있어 따로 부를 이유가 없다.
 */
@Service
@Transactional
public class ProblemBookmarkService {

    private final ProblemBookmarkRepository problemBookmarkRepository;
    private final ProblemService problemService;

    public ProblemBookmarkService(ProblemBookmarkRepository problemBookmarkRepository, ProblemService problemService) {
        this.problemBookmarkRepository = problemBookmarkRepository;
        this.problemService = problemService;
    }

    /** 이미 있으면 그대로 (멱등). 없는 문제는 404 */
    public void add(Long problemId, User user) {
        problemService.requireProblem(problemId);
        if (!problemBookmarkRepository.existsByUserIdAndProblemId(user.getId(), problemId)) {
            problemBookmarkRepository.save(ProblemBookmark.of(user.getId(), problemId));
        }
    }

    /** 없어도 204 (멱등). 없는 문제는 404 */
    public void remove(Long problemId, User user) {
        problemService.requireProblem(problemId);
        problemBookmarkRepository.deleteByUserIdAndProblemId(user.getId(), problemId);
    }
}
