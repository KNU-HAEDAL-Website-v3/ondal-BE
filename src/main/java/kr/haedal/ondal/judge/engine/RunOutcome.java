package kr.haedal.ondal.judge.engine;

import java.util.List;

/**
 * 실행 결과 - compileOutput 이 null 이 아니면 컴파일 에러(runs 는 빈 목록). 아니면 입력 순서대로 runs.
 */
public record RunOutcome(String compileOutput, List<CaseRun> runs) {

    public static RunOutcome compileError(String compileOutput) {
        return new RunOutcome(compileOutput == null || compileOutput.isBlank() ? "(컴파일 메시지 없음)" : compileOutput, List.of());
    }

    public static RunOutcome of(List<CaseRun> runs) {
        return new RunOutcome(null, runs);
    }

    public boolean isCompileError() {
        return compileOutput != null;
    }

    /** 케이스 1개의 실행 결과. status 가 OK 여야 stdout 을 비교한다 */
    public record CaseRun(int index, Status status, String stdout, String stderr, Integer timeMs, Integer memoryKb) {

        public enum Status {
            /** 정상 종료 - 출력을 기대 출력과 비교한다 */
            OK,
            TIME_LIMIT,
            MEMORY_LIMIT,
            RUNTIME_ERROR,
            /** 엔진 내부 오류 - 학생 잘못 아님 */
            ENGINE_ERROR
        }
    }
}
