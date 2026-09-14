package kr.haedal.ondal.judge.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 엔진 없음(off) - 문제 출제·저장은 그대로 되고, 제출은 채점 대기(PENDING)로 남는다.
 * Judge0 가 연결되기 전 운영 배포를 막지 않기 위한 모드 (docs judge/infra.md 3절 - 서버 결정 대기). 연결 후 기동하면 대기분이 재큐잉된다.
 */
@Component
@ConditionalOnProperty(name = JudgeEngineMode.PROPERTY, havingValue = JudgeEngineMode.OFF)
public class DisabledJudgeEngine implements JudgeEngine {

    private static final Logger log = LoggerFactory.getLogger(DisabledJudgeEngine.class);

    public DisabledJudgeEngine() {
        log.warn("[judge] 채점 엔진 off - 자동 채점 문제의 제출은 PENDING 으로 대기합니다. Judge0 연결 후 ondal.judge.engine=judge0 (JUDGE0_URL·JUDGE0_TOKEN)");
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public RunOutcome run(RunRequest request) {
        throw new JudgeEngineException("채점 엔진이 연결되어 있지 않습니다.", true);
    }
}
