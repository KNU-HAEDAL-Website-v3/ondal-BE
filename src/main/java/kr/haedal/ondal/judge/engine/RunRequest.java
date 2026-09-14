package kr.haedal.ondal.judge.engine;

import java.util.List;

/**
 * 엔진에 넘기는 실행 요청 - 소스 1개 × 입력 N개, 제한은 케이스마다 같다.
 * language 는 FE 셀렉트 문자열("C", "Python 3" ...) 그대로 - 엔진 고유 id 매핑은 구현(Judge0Engine) 안에서
 */
public record RunRequest(String language, String sourceCode, List<String> inputs, int timeLimitMs, int memoryLimitMb) {
}
