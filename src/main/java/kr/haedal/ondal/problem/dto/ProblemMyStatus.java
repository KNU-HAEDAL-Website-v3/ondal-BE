package kr.haedal.ondal.problem.dto;

/**
 * 목록·상세의 myStatus - 요청자와 문제의 관계 (docs hoj/api.md 1절).
 * SOLVED: ACCEPTED 가 하나라도 있다(연습·과제 무관, solved 와 같은 규칙) / ATTEMPTED: 채점된 제출은 있으나 아직 못 풀었다 / NONE: 채점된 제출이 없다
 */
public enum ProblemMyStatus {
    SOLVED, ATTEMPTED, NONE
}
