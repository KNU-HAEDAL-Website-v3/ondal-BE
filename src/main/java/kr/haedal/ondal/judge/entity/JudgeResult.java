package kr.haedal.ondal.judge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 채점 결과 - 제출 1건에 1행(PK = submission_id), 재채점은 같은 행을 PENDING 으로 되돌려 덮어쓴다 (docs judge/design.md 결정 12).
 * 케이스별 결과는 jsonb 문자열(caseResults) - 항상 제출 단위로 통째 읽고 쓰며 케이스별 질의가 없다.
 * Submission 을 연관으로 잇지 않고 id 만 갖는 이유: 워커가 트랜잭션 밖에서 엔진을 부르고 돌아와 결과만 갱신한다 - 지연 로딩이 끼어들 자리가 없다.
 */
@Entity
@Table(name = "judge_results", indexes = {
        @Index(name = "idx_judge_results_problem", columnList = "problem_id"),
        @Index(name = "idx_judge_results_status", columnList = "status")
})
public class JudgeResult {

    /** 컴파일 메시지·케이스 출력 저장 상한 */
    public static final int COMPILE_OUTPUT_MAX = 8 * 1024;
    public static final int CASE_OUTPUT_MAX = 4 * 1024;

    @Id
    @Column(name = "submission_id")
    private Long submissionId;

    /** 채점 기준의 출처 - 테스트케이스·실행 제한은 문제(Problem)의 것이다 (V7). 과제 제출이든 HOJ 연습 제출이든 같다 */
    @Column(name = "problem_id", nullable = false)
    private Long problemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JudgeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Verdict verdict;

    @Column(name = "passed_cases", nullable = false)
    private int passedCases;

    @Column(name = "total_cases", nullable = false)
    private int totalCases;

    @Column(name = "max_time_ms")
    private Integer maxTimeMs;

    @Column(name = "max_memory_kb")
    private Integer maxMemoryKb;

    @Column(name = "compile_output", columnDefinition = "text")
    private String compileOutput;

    /** [{position, verdict, timeMs, memoryKb, actualOutput, truncated}] - JudgeService 가 ObjectMapper 로 직렬화 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "case_results", columnDefinition = "jsonb")
    private String caseResults;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "judged_at")
    private Instant judgedAt;

    protected JudgeResult() {
    }

    private JudgeResult(Long submissionId, Long problemId) {
        this.submissionId = submissionId;
        this.problemId = problemId;
        this.status = JudgeStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /** 새 제출의 채점 대기 행 */
    public static JudgeResult pending(Long submissionId, Long problemId) {
        return new JudgeResult(submissionId, problemId);
    }

    /** 재채점 - 판정을 비우고 다시 대기로. 화면은 그동안 "채점 중" */
    public void reset() {
        this.status = JudgeStatus.PENDING;
        this.verdict = null;
        this.passedCases = 0;
        this.totalCases = 0;
        this.maxTimeMs = null;
        this.maxMemoryKb = null;
        this.compileOutput = null;
        this.caseResults = null;
        this.attempts = 0;
        this.judgedAt = null;
    }

    /** 워커가 엔진 호출을 시작할 때 - 시도 횟수를 센다 */
    public void markRunning() {
        this.status = JudgeStatus.RUNNING;
        this.attempts++;
    }

    /** 엔진이 결과를 돌려준 뒤 집계 저장 (verdict 가 JUDGE_ERROR 면 status 는 ERROR) */
    public void complete(Verdict verdict, int passedCases, int totalCases, Integer maxTimeMs, Integer maxMemoryKb,
                         String compileOutput, String caseResults) {
        this.status = verdict == Verdict.JUDGE_ERROR ? JudgeStatus.ERROR : JudgeStatus.DONE;
        this.verdict = verdict;
        this.passedCases = passedCases;
        this.totalCases = totalCases;
        this.maxTimeMs = maxTimeMs;
        this.maxMemoryKb = maxMemoryKb;
        this.compileOutput = truncate(compileOutput, COMPILE_OUTPUT_MAX);
        this.caseResults = caseResults;
        this.judgedAt = Instant.now();
    }

    /** 엔진 장애로 포기 - 재시도 한도를 넘었거나 재시도 불가 오류 */
    public void fail(String message) {
        this.status = JudgeStatus.ERROR;
        this.verdict = Verdict.JUDGE_ERROR;
        this.compileOutput = truncate(message, COMPILE_OUTPUT_MAX);
        this.caseResults = null;
        this.judgedAt = Instant.now();
    }

    /** 엔진 일시 장애 - 대기로 되돌려 다음 시도를 기다린다 (attempts 는 유지) */
    public void backToPending() {
        this.status = JudgeStatus.PENDING;
    }

    public boolean isFinished() {
        return status == JudgeStatus.DONE || status == JudgeStatus.ERROR;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getSubmissionId() { return submissionId; }
    public Long getProblemId() { return problemId; }
    public JudgeStatus getStatus() { return status; }
    public Verdict getVerdict() { return verdict; }
    public int getPassedCases() { return passedCases; }
    public int getTotalCases() { return totalCases; }
    public Integer getMaxTimeMs() { return maxTimeMs; }
    public Integer getMaxMemoryKb() { return maxMemoryKb; }
    public String getCompileOutput() { return compileOutput; }
    public String getCaseResults() { return caseResults; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getJudgedAt() { return judgedAt; }
}
