package kr.haedal.ondal.judge.entity;

/**
 * 판정 7종 (docs judge/design.md 결정 4). 케이스를 순서대로 전부 실행하고 첫 실패 케이스의 종류가 대표 판정.
 * 점수·부분 점수 없음. JUDGE_ERROR 는 학생 잘못이 아닌 엔진 장애 - 운영진 재채점으로 복구한다.
 */
public enum Verdict {
    ACCEPTED, WRONG_ANSWER, TIME_LIMIT, MEMORY_LIMIT, RUNTIME_ERROR, COMPILE_ERROR, JUDGE_ERROR
}
