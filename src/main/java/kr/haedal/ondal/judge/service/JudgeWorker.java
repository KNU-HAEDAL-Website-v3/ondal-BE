package kr.haedal.ondal.judge.service;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.judge.config.JudgeConfig;
import kr.haedal.ondal.judge.engine.JudgeEngine;
import kr.haedal.ondal.judge.engine.JudgeEngineException;
import kr.haedal.ondal.judge.engine.JudgeProperties;
import kr.haedal.ondal.judge.engine.RunOutcome;
import kr.haedal.ondal.judge.engine.RunRequest;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.JudgeStatus;
import kr.haedal.ondal.judge.entity.TestCase;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.judge.repository.TestCaseRepository;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 채점 워커 - 제출 1건을 받아 엔진을 부르고 결과를 저장한다 (docs judge/design.md 결정 5·14).
 *
 * 흐름: PENDING 확인 + RUNNING 표시(새 트랜잭션) → 제출·케이스·제한 읽기 → [트랜잭션 밖] 엔진 실행 → 집계 → DONE/ERROR 저장(새 트랜잭션)
 * 엔진 일시 장애는 지수 백오프(2s, 4s, ... 최대 256s)로 maxAttempts 까지, 그 뒤 JUDGE_ERROR. 엔진이 off(available=false)면 건드리지 않고 PENDING 에 둔다.
 * 새 트랜잭션(REQUIRES_NEW)을 쓰는 이유: AFTER_COMMIT 리스너 안에서 기본 전파(REQUIRED)로 시작한 트랜잭션은 이미 끝난 트랜잭션에 "참여"해 커밋되지 않는다.
 */
@Component
public class JudgeWorker {

    private static final Logger log = LoggerFactory.getLogger(JudgeWorker.class);

    private final JudgeEngine engine;
    private final JudgeProperties properties;
    private final JudgeAggregator aggregator;
    private final JudgeResultRepository judgeResultRepository;
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final TransactionTemplate newTransaction;
    private final TaskExecutor executor;

    public JudgeWorker(JudgeEngine engine, JudgeProperties properties, JudgeAggregator aggregator,
                       JudgeResultRepository judgeResultRepository, SubmissionRepository submissionRepository,
                       TestCaseRepository testCaseRepository, PlatformTransactionManager transactionManager,
                       @org.springframework.beans.factory.annotation.Qualifier(JudgeConfig.EXECUTOR) TaskExecutor executor) {
        this.engine = engine;
        this.properties = properties;
        this.aggregator = aggregator;
        this.judgeResultRepository = judgeResultRepository;
        this.submissionRepository = submissionRepository;
        this.testCaseRepository = testCaseRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
    }

    /** 제출·재채점 트랜잭션이 커밋된 뒤 실행. 트랜잭션 밖에서 발행된 이벤트(기동 재큐잉)도 받는다(fallbackExecution) */
    @Async(JudgeConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(SubmissionJudgeRequested event) {
        judge(event.submissionId());
    }

    /** 서버 재시작으로 끊긴 채점(PENDING·RUNNING)을 다시 큐에 - 엔진이 off 면 다음 기동으로 미룬다 */
    @EventListener(ApplicationReadyEvent.class)
    public void requeueUnfinished() {
        if (!engine.available()) {
            return;
        }
        List<JudgeResult> unfinished = judgeResultRepository.findAllByStatusIn(List.of(JudgeStatus.PENDING, JudgeStatus.RUNNING));
        if (unfinished.isEmpty()) {
            return;
        }
        log.info("[judge] 기동 재큐잉 {}건", unfinished.size());
        for (JudgeResult result : unfinished) {
            Long id = result.getSubmissionId();
            newTransaction.executeWithoutResult(tx -> judgeResultRepository.findById(id).ifPresent(JudgeResult::backToPending));
            executor.execute(() -> judge(id));
        }
    }

    /** 제출 1건 채점 - 어디서 불려도 안전(PENDING 이 아니면 아무 일도 하지 않는다) */
    public void judge(Long submissionId) {
        if (!engine.available()) {
            return;
        }
        int maxAttempts = Math.max(1, properties.judge0() == null ? 8 : properties.judge0().maxAttempts());
        while (true) {
            Integer attempt = newTransaction.execute(tx -> {
                Optional<JudgeResult> found = judgeResultRepository.findById(submissionId);
                if (found.isEmpty() || found.get().getStatus() != JudgeStatus.PENDING) {
                    return null;
                }
                found.get().markRunning();
                return found.get().getAttempts();
            });
            if (attempt == null) {
                return;
            }

            Work work = newTransaction.execute(tx -> loadWork(submissionId));
            if (work == null) {
                return;
            }
            if (work.failure != null) {
                newTransaction.executeWithoutResult(tx -> judgeResultRepository.findById(submissionId).ifPresent(r -> r.fail(work.failure)));
                return;
            }

            try {
                RunOutcome outcome = engine.run(work.request);
                JudgeAggregator.Aggregate aggregate = aggregator.aggregate(outcome, work.testCases);
                String casesJson = aggregator.toJson(aggregate.cases());
                newTransaction.executeWithoutResult(tx -> judgeResultRepository.findById(submissionId).ifPresent(r ->
                        r.complete(aggregate.verdict(), aggregate.passedCases(), aggregate.totalCases(),
                                aggregate.maxTimeMs(), aggregate.maxMemoryKb(), aggregate.compileOutput(), casesJson)));
                return;
            } catch (JudgeEngineException e) {
                boolean retry = e.isRetryable() && attempt < maxAttempts;
                log.warn("[judge] 제출 {} 채점 실패 (시도 {}/{}, 재시도 {}): {}", submissionId, attempt, maxAttempts, retry, e.getMessage());
                if (!retry) {
                    newTransaction.executeWithoutResult(tx -> judgeResultRepository.findById(submissionId).ifPresent(r ->
                            r.fail("채점 엔진 오류: " + e.getMessage())));
                    return;
                }
                newTransaction.executeWithoutResult(tx -> judgeResultRepository.findById(submissionId).ifPresent(JudgeResult::backToPending));
                sleepBackoff(attempt);
            } catch (RuntimeException e) {
                log.error("[judge] 제출 {} 채점 중 예상 못 한 오류", submissionId, e);
                newTransaction.executeWithoutResult(tx -> judgeResultRepository.findById(submissionId).ifPresent(r ->
                        r.fail("채점 처리 오류: " + e.getClass().getSimpleName())));
                return;
            }
        }
    }

    /** 제출·과제·케이스를 한 트랜잭션에서 읽어 엔진 요청으로 - 지연 로딩을 여기서 끝낸다 */
    private Work loadWork(Long submissionId) {
        Optional<Submission> found = submissionRepository.findById(submissionId);
        if (found.isEmpty()) {
            return null;   // 과제 삭제 연쇄로 사라짐 - 결과 행도 함께 지워졌다
        }
        Submission submission = found.get();
        Assignment assignment = submission.getAssignment();
        List<TestCase> testCases = testCaseRepository.findAllByAssignmentIdOrderByPositionAsc(assignment.getId());
        if (testCases.isEmpty()) {
            return Work.failure("테스트케이스가 없어 채점하지 않았습니다. 운영진이 케이스를 저장한 뒤 재채점하세요.");
        }
        if (submission.getCodeText() == null || !properties.supportsLanguage(submission.getLanguage())) {
            return Work.failure("자동 채점을 지원하지 않는 제출입니다: " + submission.getLanguage());
        }
        RunRequest request = new RunRequest(submission.getLanguage(), submission.getCodeText(),
                testCases.stream().map(TestCase::getInput).toList(),
                properties.effectiveTimeLimitMs(assignment.getTimeLimitMs()),
                properties.effectiveMemoryLimitMb(assignment.getMemoryLimitMb()));
        return new Work(request, testCases, null);
    }

    private record Work(RunRequest request, List<TestCase> testCases, String failure) {
        static Work failure(String message) {
            return new Work(null, List.of(), message);
        }
    }

    private static void sleepBackoff(int attempt) {
        long seconds = Math.min(256, 1L << Math.min(attempt, 8));
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
