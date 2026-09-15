package kr.haedal.ondal.problem.service;

import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.judge.repository.TestCaseRepository;
import kr.haedal.ondal.judge.service.JudgeService;
import kr.haedal.ondal.problem.dto.PracticeSubmitRequest;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.submission.dto.SubmissionResponse;
import kr.haedal.ondal.submission.dto.SubmissionSummary;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HOJ 연습 제출 - 분반 과제와 무관하게 문제를 직접 풀어 채점받는 경로 (V7).
 *
 * 과제 제출과 **같은 파이프라인**을 쓴다 (CLAUDE.md 원칙 1 - 코드 두 벌 금지):
 * 같은 submissions 테이블, 같은 JudgeService.enqueueIfJudged, 같은 JudgeWorker·비교기·판정.
 * 다른 점은 대상이 assignment 가 아니라 problem 이라는 것뿐 - 그래서 마감·지각·코멘트·현황판이 전부 없다.
 *
 * 제출은 로그인한 누구나 - 분반에 소속되지 않은 부원도 문제를 풀 수 있어야 HOJ 가 의미를 갖는다 (2026-09-15 PM).
 * 남의 연습 제출은 어떤 경로로도 보이지 않는다 - 조회는 항상 (problemId, submissionId, 본인) 으로 좁힌다.
 */
@Service
@Transactional
public class PracticeSubmissionService {

    private final ProblemService problemService;
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final JudgeResultRepository judgeResultRepository;
    private final JudgeService judgeService;

    public PracticeSubmissionService(ProblemService problemService, SubmissionRepository submissionRepository,
                                     TestCaseRepository testCaseRepository, JudgeResultRepository judgeResultRepository,
                                     JudgeService judgeService) {
        this.problemService = problemService;
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.judgeResultRepository = judgeResultRepository;
        this.judgeService = judgeService;
    }

    /**
     * 연습 제출 - 저장 후 바로 채점 큐에 올린다.
     * 테스트케이스가 없는 문제는 409: 채점받으려고 푸는 곳인데 기준이 없으면 제출해도 영영 대기 상태로 남는다.
     */
    public SubmissionResponse submit(Long problemId, PracticeSubmitRequest request, User user) {
        Problem problem = problemService.requireProblem(problemId);
        if (testCaseRepository.countByProblemId(problemId) == 0) {
            throw new ConflictException("아직 채점 기준(테스트케이스)이 없는 문제예요. 운영진이 등록한 뒤에 풀 수 있어요.");
        }
        String language = request.language().strip();
        judgeService.validateSubmittable(problem, SubmissionType.CODE, language);

        Submission submission = submissionRepository.save(
                Submission.practice(problem, user, request.codeText(), language));
        JudgeResult judge = judgeService.enqueueIfJudged(submission);
        return SubmissionResponse.practice(submission, UserSummary.of(user, null),   // HOJ 는 분반 문맥이 없다 - 직책은 전역 역할로만 정해진다
                judge == null ? null : judgeService.toResultResponse(judge));
    }

    /** 내 연습 제출 이력 - 최신이 앞. 남의 기록은 조회할 수 없다 */
    @Transactional(readOnly = true)
    public List<SubmissionSummary> findMine(Long problemId, User user) {
        problemService.requireProblem(problemId);
        List<Submission> mine = submissionRepository.findAllByProblemIdAndUserIdOrderBySubmittedAtDesc(problemId, user.getId());
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, JudgeResult> judges = judgeResultRepository
                .findAllBySubmissionIdIn(mine.stream().map(Submission::getId).toList()).stream()
                .collect(Collectors.toMap(JudgeResult::getSubmissionId, Function.identity()));
        return mine.stream()
                .map(submission -> SubmissionSummary.practice(submission, judges.get(submission.getId())))
                .toList();
    }

    /** 내 연습 제출 단건 - 코드 전문 + 채점 결과. 남의 것이면 404(존재 비노출) */
    @Transactional(readOnly = true)
    public SubmissionResponse findOne(Long problemId, Long submissionId, User user) {
        Submission submission = submissionRepository
                .findByIdAndProblemIdAndUserId(submissionId, problemId, user.getId())
                .orElseThrow(() -> new NotFoundException("제출을 찾을 수 없습니다."));
        return SubmissionResponse.practice(submission, UserSummary.of(user, null), judgeService.resultOf(submission));
    }
}
