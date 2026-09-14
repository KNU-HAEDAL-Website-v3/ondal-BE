package kr.haedal.ondal.judge.service;

import kr.haedal.ondal.judge.dto.JudgeCaseResult;
import kr.haedal.ondal.judge.engine.RunOutcome;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.TestCase;
import kr.haedal.ondal.judge.entity.Verdict;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 엔진 실행 결과 + 테스트케이스 → 판정 집계 (docs judge/design.md 결정 4).
 * 케이스 순서대로 판정하고 첫 실패의 종류가 대표 판정, 전부 통과면 ACCEPTED. 시간·메모리는 케이스 최대값.
 */
@Component
public class JudgeAggregator {

    private final ObjectMapper objectMapper;

    public JudgeAggregator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record Aggregate(Verdict verdict, int passedCases, int totalCases, Integer maxTimeMs, Integer maxMemoryKb,
                            String compileOutput, List<JudgeCaseResult> cases) {
    }

    public Aggregate aggregate(RunOutcome outcome, List<TestCase> testCases) {
        if (outcome.isCompileError()) {
            return new Aggregate(Verdict.COMPILE_ERROR, 0, testCases.size(), null, null, outcome.compileOutput(), List.of());
        }
        List<JudgeCaseResult> cases = new ArrayList<>();
        Verdict overall = Verdict.ACCEPTED;
        int passed = 0;
        Integer maxTime = null;
        Integer maxMemory = null;
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            RunOutcome.CaseRun run = i < outcome.runs().size() ? outcome.runs().get(i) : null;
            Verdict verdict = verdictOf(run, testCase);
            if (verdict == Verdict.ACCEPTED) {
                passed++;
            } else if (overall == Verdict.ACCEPTED) {
                overall = verdict;
            }
            if (run != null) {
                maxTime = max(maxTime, run.timeMs());
                maxMemory = max(maxMemory, run.memoryKb());
            }
            String actual = run == null ? "" : run.stdout();
            boolean truncated = actual != null && actual.length() > JudgeResult.CASE_OUTPUT_MAX;
            cases.add(new JudgeCaseResult(testCase.getPosition(), verdict,
                    run == null ? null : run.timeMs(), run == null ? null : run.memoryKb(),
                    JudgeResult.truncate(actual, JudgeResult.CASE_OUTPUT_MAX), truncated));
        }
        return new Aggregate(overall, passed, testCases.size(), maxTime, maxMemory, null, cases);
    }

    private static Verdict verdictOf(RunOutcome.CaseRun run, TestCase testCase) {
        if (run == null) {
            return Verdict.JUDGE_ERROR;
        }
        return switch (run.status()) {
            case OK -> OutputComparator.matches(testCase.getExpectedOutput(), run.stdout()) ? Verdict.ACCEPTED : Verdict.WRONG_ANSWER;
            case TIME_LIMIT -> Verdict.TIME_LIMIT;
            case MEMORY_LIMIT -> Verdict.MEMORY_LIMIT;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
            case ENGINE_ERROR -> Verdict.JUDGE_ERROR;
        };
    }

    private static Integer max(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    public String toJson(List<JudgeCaseResult> cases) {
        return objectMapper.writeValueAsString(cases);
    }

    public List<JudgeCaseResult> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, JudgeCaseResult.class));
    }
}
