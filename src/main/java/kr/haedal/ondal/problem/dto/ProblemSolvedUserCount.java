package kr.haedal.ondal.problem.dto;

/** 문제별 "푼 사람 수" 집계 (JudgeResultRepository 조회 결과) - ACCEPTED 판정이 있는 사용자 수, 연습·과제 합산 */
public record ProblemSolvedUserCount(Long problemId, Long count) {
}
