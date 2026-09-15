package kr.haedal.ondal.problem.dto;

/** 문제별 배정 횟수 집계 - 목록의 assignedCount·삭제 가능 여부 조립용 */
public record ProblemAssignedCount(Long problemId, Long count) {
}
