package kr.haedal.ondal.judge.service;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.common.error.ServiceUnavailableException;
import kr.haedal.ondal.judge.dto.JudgeCaseResult;
import kr.haedal.ondal.judge.dto.JudgeConfigRequest;
import kr.haedal.ondal.judge.dto.JudgeConfigResponse;
import kr.haedal.ondal.judge.dto.JudgeResultResponse;
import kr.haedal.ondal.judge.dto.JudgeRunRequest;
import kr.haedal.ondal.judge.dto.JudgeRunResponse;
import kr.haedal.ondal.judge.dto.JudgeSamplesResponse;
import kr.haedal.ondal.judge.dto.RejudgeResponse;
import kr.haedal.ondal.judge.dto.TestCaseRequest;
import kr.haedal.ondal.judge.dto.TestCaseResponse;
import kr.haedal.ondal.judge.engine.JudgeEngine;
import kr.haedal.ondal.judge.engine.JudgeEngineException;
import kr.haedal.ondal.judge.engine.JudgeProperties;
import kr.haedal.ondal.judge.engine.RunOutcome;
import kr.haedal.ondal.judge.engine.RunRequest;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.TestCase;
import kr.haedal.ondal.judge.entity.Verdict;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.judge.repository.TestCaseRepository;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.repository.ProblemRepository;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 자동 채점 - 채점 설정·테스트케이스(#47·#48·#50), 출제 도구 실행(#49), 재채점(#51), 제출 훅, 응답 조립, 삭제 연쇄 (docs judge/design.md).
 *
 * V7 이후 채점 기준(테스트케이스·실행 제한)은 문제(Problem)의 것이다 - 같은 문제를 여러 분반에 배정해도 기준은 하나다.
 * 그래서 설정·예시·출제 도구는 문제 스코프(/api/problems/{problemId}/...)이고, 재채점만 과제 스코프로 남는다(운영진이 "내 반 것만" 다시 돌리는 동작).
 * 실제 채점(엔진 호출·집계 저장)은 JudgeWorker - 이 서비스는 PENDING 행을 만들고 커밋 후 이벤트를 발행할 뿐이다.
 * 메서드마다 @Transactional 을 따로 단다 - #49 는 엔진을 최대 30초 기다리므로 트랜잭션(DB 연결) 밖에서 돌아야 한다.
 */
@Service
public class JudgeService {

    public static final String UNAVAILABLE_CODE = "JUDGE_UNAVAILABLE";

    private final TestCaseRepository testCaseRepository;
    private final JudgeResultRepository judgeResultRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final ProblemRepository problemRepository;
    private final CohortRepository cohortRepository;
    private final JudgeEngine engine;
    private final JudgeProperties properties;
    private final JudgeAggregator aggregator;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate readOnlyTx;

    public JudgeService(TestCaseRepository testCaseRepository, JudgeResultRepository judgeResultRepository,
                        SubmissionRepository submissionRepository, AssignmentRepository assignmentRepository,
                        ProblemRepository problemRepository,
                        CohortRepository cohortRepository, JudgeEngine engine, JudgeProperties properties,
                        JudgeAggregator aggregator, ApplicationEventPublisher events, PlatformTransactionManager transactionManager) {
        this.testCaseRepository = testCaseRepository;
        this.judgeResultRepository = judgeResultRepository;
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.problemRepository = problemRepository;
        this.cohortRepository = cohortRepository;
        this.engine = engine;
        this.properties = properties;
        this.aggregator = aggregator;
        this.events = events;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    // ---- #47 #48 #50 설정 ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public JudgeConfigResponse getConfig(Long problemId) {
        Problem problem = requireProblem(problemId);
        return toConfigResponse(problem, testCaseRepository.findAllByProblemIdOrderByPositionAsc(problemId), 0);
    }

    /**
     * 통째 교체 - 기존 케이스 전부 삭제 후 순서대로 재삽입. rejudge=true 면 이 문제로 채점되는 코드 제출을 다시 큐에 (design.md 결정 9).
     * 재채점 대상이 "이 문제의 모든 제출"인 이유: 채점 기준이 바뀌면 그 기준으로 매겨진 판정은 배정이 어디든 전부 낡은 값이 된다.
     */
    @Transactional
    public JudgeConfigResponse saveConfig(Long problemId, JudgeConfigRequest request) {
        Problem problem = requireProblem(problemId);
        validateLimits(request.timeLimitMs(), request.memoryLimitMb());
        if (request.testCases().size() > properties.maxTestCases()) {
            throw new InvalidInputException("테스트케이스는 최대 " + properties.maxTestCases() + "개까지 저장할 수 있습니다.");
        }
        problem.updateJudgeLimits(request.timeLimitMs(), request.memoryLimitMb());
        testCaseRepository.deleteAllByProblemId(problemId);
        List<TestCase> saved = new ArrayList<>();
        int position = 0;
        for (TestCaseRequest tc : request.testCases()) {
            saved.add(testCaseRepository.save(TestCase.create(problem, position++, tc.input(), tc.expectedOutput(), tc.publicFlag())));
        }
        int queued = request.rejudge() && !saved.isEmpty() ? rejudgeAllOfProblem(problem) : 0;
        return toConfigResponse(problem, saved, queued);
    }

    @Transactional(readOnly = true)
    public JudgeSamplesResponse samples(Long problemId) {
        Problem problem = requireProblem(problemId);
        long total = testCaseRepository.countByProblemId(problemId);
        List<TestCase> publics = testCaseRepository.findAllByProblemIdAndIsPublicTrueOrderByPositionAsc(problemId);
        return new JudgeSamplesResponse(total > 0,
                properties.effectiveTimeLimitMs(problem.getTimeLimitMs()),
                properties.effectiveMemoryLimitMb(problem.getMemoryLimitMb()),
                languages(),
                publics.stream().map(t -> new JudgeSamplesResponse.Sample(t.getPosition(), t.getInput(), t.getExpectedOutput())).toList());
    }

    // ---- #49 출제 도구 -----------------------------------------------------------------------

    /** 저장 없이 실행 - DB 확인은 짧은 읽기 트랜잭션, 엔진 대기는 트랜잭션 밖 */
    public JudgeRunResponse run(Long problemId, JudgeRunRequest request) {
        readOnlyTx.executeWithoutResult(tx -> requireProblem(problemId));
        if (!properties.supportsLanguage(request.language())) {
            throw new InvalidInputException("이 언어는 자동 채점을 지원하지 않습니다: " + request.language() + " (지원: " + String.join(", ", languages()) + ")");
        }
        if (request.expectedOutputs() != null && request.expectedOutputs().size() != request.inputs().size()) {
            throw new InvalidInputException("기대 출력 개수는 입력 개수와 같아야 합니다.");
        }
        validateLimits(request.timeLimitMs(), request.memoryLimitMb());
        if (!engine.available()) {
            throw new ServiceUnavailableException(UNAVAILABLE_CODE, "채점 엔진이 연결되어 있지 않습니다. 저장은 할 수 있고, 실행·검증은 엔진 연결 뒤에 가능합니다.");
        }
        RunOutcome outcome;
        try {
            outcome = engine.run(new RunRequest(request.language(), request.sourceCode(), request.inputs(),
                    properties.effectiveTimeLimitMs(request.timeLimitMs()), properties.effectiveMemoryLimitMb(request.memoryLimitMb())));
        } catch (JudgeEngineException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_CODE, "채점 엔진 오류: " + e.getMessage());
        }
        if (outcome.isCompileError()) {
            return new JudgeRunResponse(outcome.compileOutput(), List.of());
        }
        List<JudgeRunResponse.Run> runs = new ArrayList<>();
        for (int i = 0; i < request.inputs().size(); i++) {
            RunOutcome.CaseRun run = i < outcome.runs().size() ? outcome.runs().get(i) : null;
            String expected = request.expectedOutputs() == null ? null : request.expectedOutputs().get(i);
            runs.add(new JudgeRunResponse.Run(i,
                    run == null ? "" : run.stdout(),
                    run == null ? "" : run.stderr(),
                    verdictForRun(run, expected),
                    run == null ? null : run.timeMs(),
                    run == null ? null : run.memoryKb()));
        }
        return new JudgeRunResponse(null, runs);
    }

    /** 기대 출력이 없으면 정상 종료는 null(판정 없음), 실행 실패는 그 종류. 있으면 비교기로 판정 */
    private static Verdict verdictForRun(RunOutcome.CaseRun run, String expected) {
        if (run == null) {
            return Verdict.JUDGE_ERROR;
        }
        return switch (run.status()) {
            case OK -> expected == null ? null : (OutputComparator.matches(expected, run.stdout()) ? Verdict.ACCEPTED : Verdict.WRONG_ANSWER);
            case TIME_LIMIT -> Verdict.TIME_LIMIT;
            case MEMORY_LIMIT -> Verdict.MEMORY_LIMIT;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
            case ENGINE_ERROR -> Verdict.JUDGE_ERROR;
        };
    }

    // ---- #51 재채점 ---------------------------------------------------------------------------

    /** 과제 단위 재채점 - "내 반 제출만 다시 돌린다". 같은 문제를 쓰는 다른 분반은 건드리지 않는다 */
    @Transactional
    public RejudgeResponse rejudge(Long cohortId, Long assignmentId) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        Long problemId = assignment.getProblem().getId();
        if (testCaseRepository.countByProblemId(problemId) == 0) {
            throw new ConflictException("테스트케이스가 없는 문제는 재채점할 수 없습니다.");
        }
        return new RejudgeResponse(requeue(
                submissionRepository.findAllByAssignmentIdAndType(assignment.getId(), SubmissionType.CODE), problemId));
    }

    /** 채점 기준이 바뀌었을 때 - 이 문제로 채점되는 제출 전부(여러 분반의 과제 제출 + HOJ 연습 제출) */
    private int rejudgeAllOfProblem(Problem problem) {
        return requeue(submissionRepository.findAllTargetingProblem(problem.getId(), SubmissionType.CODE), problem.getId());
    }

    /** PENDING 으로 되돌리고(없던 행은 새로) 커밋 후 채점 이벤트 */
    private int requeue(List<Submission> codes, Long problemId) {
        for (Submission submission : codes) {
            JudgeResult result = judgeResultRepository.findById(submission.getId())
                    .orElseGet(() -> JudgeResult.pending(submission.getId(), problemId));
            result.reset();
            judgeResultRepository.save(result);
            events.publishEvent(new SubmissionJudgeRequested(submission.getId()));
        }
        return codes.size();
    }

    // ---- 제출(#18) 훅 - SubmissionService 의 트랜잭션 안에서 -------------------------------------------

    /** 자동 채점 문제의 CODE 제출은 지원 언어여야 한다 → 400. 케이스 없는 문제·FILE·LINK 는 그대로 */
    public void validateSubmittable(Problem problem, SubmissionType type, String language) {
        if (type != SubmissionType.CODE || testCaseRepository.countByProblemId(problem.getId()) == 0) {
            return;
        }
        if (!properties.supportsLanguage(language)) {
            throw new InvalidInputException("이 문제는 자동 채점 문제입니다. 지원 언어로 제출하세요: " + String.join(", ", languages()));
        }
    }

    /** 저장된 제출이 채점 대상이면 PENDING 행을 만들고 커밋 뒤 채점을 요청한다. 대상이 아니면 null */
    public JudgeResult enqueueIfJudged(Submission submission) {
        if (submission.getType() != SubmissionType.CODE) {
            return null;
        }
        Long problemId = submission.targetProblem().getId();
        if (testCaseRepository.countByProblemId(problemId) == 0) {
            return null;
        }
        JudgeResult result = judgeResultRepository.save(JudgeResult.pending(submission.getId(), problemId));
        events.publishEvent(new SubmissionJudgeRequested(submission.getId()));
        return result;
    }

    // ---- 응답 조립 (호출자 트랜잭션 안) --------------------------------------------------------------

    public JudgeResultResponse resultOf(Submission submission) {
        return judgeResultRepository.findById(submission.getId()).map(this::toResultResponse).orElse(null);
    }

    public Map<Long, JudgeResult> resultsOf(Collection<Long> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return judgeResultRepository.findAllBySubmissionIdIn(submissionIds).stream()
                .collect(Collectors.toMap(JudgeResult::getSubmissionId, Function.identity()));
    }

    /** 공개 케이스만 입력·기대 출력·실제 출력을 싣는다 - 운영진에게도 같은 규칙(비공개 입력은 #47) */
    public JudgeResultResponse toResultResponse(JudgeResult result) {
        Map<Integer, TestCase> byPosition = testCaseRepository.findAllByProblemIdOrderByPositionAsc(result.getProblemId()).stream()
                .collect(Collectors.toMap(TestCase::getPosition, Function.identity()));
        List<JudgeResultResponse.Case> cases = aggregator.fromJson(result.getCaseResults()).stream()
                .map(c -> toCaseView(c, byPosition.get(c.position())))
                .toList();
        return new JudgeResultResponse(result.getStatus(), result.getVerdict(), result.getPassedCases(), result.getTotalCases(),
                result.getMaxTimeMs(), result.getMaxMemoryKb(), result.getCompileOutput(), cases, result.getJudgedAt());
    }

    private static JudgeResultResponse.Case toCaseView(JudgeCaseResult c, TestCase testCase) {
        boolean isPublic = testCase != null && testCase.isPublic();
        return new JudgeResultResponse.Case(c.position(), c.verdict(), c.timeMs(), c.memoryKb(), isPublic,
                isPublic ? testCase.getInput() : null,
                isPublic ? testCase.getExpectedOutput() : null,
                isPublic ? c.actualOutput() : null,
                isPublic && c.truncated());
    }

    // ---- 삭제 연쇄 - AssignmentService.delete 가 제출 삭제 전에 부른다 --------------------------------

    /**
     * 과제 삭제 연쇄 - 그 과제의 제출에 달린 채점 결과만 지운다.
     * 테스트케이스는 문제의 것이므로 건드리지 않는다 - 같은 문제를 쓰는 다른 분반의 채점 기준이 사라지면 안 된다.
     */
    public void deleteAllOf(Long assignmentId) {
        judgeResultRepository.deleteAllByAssignmentId(assignmentId);
    }

    // ---- 내부 ---------------------------------------------------------------------------------

    private JudgeConfigResponse toConfigResponse(Problem problem, List<TestCase> cases, int queued) {
        int affected = (int) submissionRepository.countTargetingProblem(problem.getId(), SubmissionType.CODE);
        return new JudgeConfigResponse(!cases.isEmpty(), engine.available(),
                properties.effectiveTimeLimitMs(problem.getTimeLimitMs()),
                properties.effectiveMemoryLimitMb(problem.getMemoryLimitMb()),
                properties.defaultTimeLimitMs(), properties.defaultMemoryLimitMb(),
                properties.maxTimeLimitMs(), properties.maxMemoryLimitMb(), properties.maxTestCases(),
                languages(),
                cases.stream().map(TestCaseResponse::of).toList(),
                affected, queued);
    }

    private void validateLimits(Integer timeLimitMs, Integer memoryLimitMb) {
        if (timeLimitMs != null && (timeLimitMs < JudgeProperties.MIN_TIME_LIMIT_MS || timeLimitMs > properties.maxTimeLimitMs())) {
            throw new InvalidInputException("시간 제한은 " + JudgeProperties.MIN_TIME_LIMIT_MS + "~" + properties.maxTimeLimitMs() + "ms 사이여야 합니다.");
        }
        if (memoryLimitMb != null && (memoryLimitMb < JudgeProperties.MIN_MEMORY_LIMIT_MB || memoryLimitMb > properties.maxMemoryLimitMb())) {
            throw new InvalidInputException("메모리 제한은 " + JudgeProperties.MIN_MEMORY_LIMIT_MB + "~" + properties.maxMemoryLimitMb() + "MB 사이여야 합니다.");
        }
    }

    private List<String> languages() {
        return properties.languages() == null ? List.of() : List.copyOf(properties.languages().keySet());
    }

    private Problem requireProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다."));
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }

    private Assignment requireAssignment(Long cohortId, Long assignmentId) {
        return assignmentRepository.findByIdAndCohortId(assignmentId, cohortId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
    }
}
