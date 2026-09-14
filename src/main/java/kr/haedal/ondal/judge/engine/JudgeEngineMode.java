package kr.haedal.ondal.judge.engine;

/**
 * 채점 엔진 모드 - 설정 키 ondal.judge.engine 의 값. 빈 등록 조건(@ConditionalOnProperty)에 쓰는 상수 (AuthMode 와 같은 패턴).
 *
 *  - fake:   지시 주석·echo 로 결정적 결과 (local·test 기본값). prod 에서는 기동 거부
 *  - judge0: Judge0 HTTP API - url·token 필수 (prod)
 *  - off:    엔진 없음 - 문제 출제·저장은 되고 채점은 PENDING 으로 대기. prod 기본값(엔진 연결 전 배포를 막지 않기 위해)
 */
public final class JudgeEngineMode {

    public static final String PROPERTY = "ondal.judge.engine";
    public static final String FAKE = "fake";
    public static final String JUDGE0 = "judge0";
    public static final String OFF = "off";

    private JudgeEngineMode() {
    }
}
