package kr.haedal.ondal.judge.service;

/** "이 제출을 채점해 달라" 이벤트 - 제출 트랜잭션이 커밋된 뒤(AFTER_COMMIT) JudgeWorker 가 받는다 */
public record SubmissionJudgeRequested(Long submissionId) {
}
