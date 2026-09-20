package kr.haedal.ondal.submission.dto;

/** 사용자별 연습 제출 수 (SubmissionRepository 조회 결과) - 랭킹 행의 submissionCount 조립용 */
public record UserSubmissionCount(Long userId, Long count) {
}
