package kr.haedal.ondal.judge.engine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가짜 엔진 (local·test) - 소스의 지시 주석으로 결과를 결정한다. 실행은 하지 않는다.
 *
 *  - 지시 없음: 각 입력을 그대로 stdout 으로 (echo) → 기대 출력을 입력과 같게 두면 ACCEPTED
 *  - `judge: WA` / `judge: TLE` / `judge: MLE` / `judge: RE` / `judge: CE` / `judge: ERROR` - 전 케이스에 적용
 *  - `judge: WA@1` 처럼 `@인덱스`(0부터) 를 붙이면 그 케이스만 - 나머지는 echo
 *  - `judge: ERROR` 는 엔진 장애 흉내(재시도 불가) → JUDGE_ERROR
 * prod 프로필에서는 기동을 막는다 - 설정 실수로 가짜 채점이 운영에 올라가는 사고 방지 (StubAuthService 와 같은 안전장치).
 */
@Component
@ConditionalOnProperty(name = JudgeEngineMode.PROPERTY, havingValue = JudgeEngineMode.FAKE, matchIfMissing = true)
public class FakeJudgeEngine implements JudgeEngine {

    private static final Pattern DIRECTIVE = Pattern.compile("judge:\\s*(AC|WA|TLE|MLE|RE|CE|ERROR)(?:@(\\d+))?");

    public FakeJudgeEngine(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("prod 프로필에서 ondal.judge.engine=fake 는 허용되지 않습니다. judge0 또는 off 로 지정하세요.");
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public RunOutcome run(RunRequest request) {
        Matcher matcher = DIRECTIVE.matcher(request.sourceCode() == null ? "" : request.sourceCode());
        String directive = null;
        Integer onlyIndex = null;
        if (matcher.find()) {
            directive = matcher.group(1);
            onlyIndex = matcher.group(2) == null ? null : Integer.parseInt(matcher.group(2));
        }
        if ("ERROR".equals(directive)) {
            throw new JudgeEngineException("fake engine: 지시된 장애", false);
        }
        if ("CE".equals(directive)) {
            return RunOutcome.compileError("fake compile error: 지시된 컴파일 에러");
        }
        List<RunOutcome.CaseRun> runs = new ArrayList<>();
        for (int i = 0; i < request.inputs().size(); i++) {
            boolean applies = directive != null && (onlyIndex == null || onlyIndex == i);
            String input = request.inputs().get(i);
            runs.add(switch (applies ? directive : "AC") {
                case "WA" -> new RunOutcome.CaseRun(i, RunOutcome.CaseRun.Status.OK, "WRONG\n", "", 5, 1024);
                case "TLE" -> new RunOutcome.CaseRun(i, RunOutcome.CaseRun.Status.TIME_LIMIT, "", "", request.timeLimitMs(), 1024);
                case "MLE" -> new RunOutcome.CaseRun(i, RunOutcome.CaseRun.Status.MEMORY_LIMIT, "", "", 5, request.memoryLimitMb() * 1024);
                case "RE" -> new RunOutcome.CaseRun(i, RunOutcome.CaseRun.Status.RUNTIME_ERROR, "", "Segmentation fault", 5, 1024);
                default -> new RunOutcome.CaseRun(i, RunOutcome.CaseRun.Status.OK, input, "", 3, 1024);
            });
        }
        return RunOutcome.of(runs);
    }
}
