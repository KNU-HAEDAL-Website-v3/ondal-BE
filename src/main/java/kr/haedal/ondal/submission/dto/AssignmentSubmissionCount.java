package kr.haedal.ondal.submission.dto;

/** 과제별 제출 건수 집계 - AssignmentResponse의 submissionCount(운영진 전용) 조립용 */
public record AssignmentSubmissionCount(Long assignmentId, Long count) {
}
