package kr.haedal.ondal.judge.engine;

/**
 * 채점 엔진 - 코드를 입력들에 대해 "실행"만 한다. 판정(맞았습니다/틀렸습니다)은 서비스의 OutputComparator 몫 (docs judge/design.md 결정 3·6).
 *
 * 구현은 설정 ondal.judge.engine 으로 하나만 뜬다 (인증 모드 stub/oidc 와 같은 패턴 - JudgeEngineMode):
 *  - judge0: Judge0 HTTP API (prod)
 *  - fake:   지시 주석·echo 로 결정적 결과 (local·test)
 *  - off:    엔진 없음 - 채점 대기(PENDING)로 남긴다. 엔진이 연결되면 기동 시 재큐잉으로 처리된다
 */
public interface JudgeEngine {

    /** 엔진을 지금 부를 수 있는가 - false 면 워커는 시도 자체를 하지 않고 PENDING 으로 둔다 */
    boolean available();

    /**
     * 소스 하나를 입력 N개에 대해 실행한다. 컴파일 에러면 compileOutput 만 채우고 케이스는 빈 목록.
     * 엔진 장애는 JudgeEngineException(retryable 로 재시도 여부 표시) - 학생 코드의 실패(TLE·RE)는 예외가 아니라 CaseRun.status 다.
     */
    RunOutcome run(RunRequest request) throws JudgeEngineException;
}
