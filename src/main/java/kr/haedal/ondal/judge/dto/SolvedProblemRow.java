package kr.haedal.ondal.judge.dto;

import java.time.Instant;

/**
 * 랭킹 원자료 (JudgeResultRepository 조회 결과) - (사용자, 문제) 하나에 그 문제를 처음 맞힌 제출 시각.
 * 사용자별 푼 문제 수와 "그 수에 도달한 시각"(firstAcceptedAt 의 최댓값)은 서비스가 이 행들로 센다 - 동아리 규모라 전부 읽어도 작다
 */
public record SolvedProblemRow(Long userId, Long problemId, Instant firstAcceptedAt) {
}
