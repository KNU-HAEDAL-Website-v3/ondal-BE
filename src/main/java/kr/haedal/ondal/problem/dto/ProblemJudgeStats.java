package kr.haedal.ondal.problem.dto;

/** 문제별 채점 집계 (JudgeResultRepository 조회 결과) - 채점된 제출 수(judge_results 행 수)와 그중 ACCEPTED 수. 연습·과제 합산 */
public record ProblemJudgeStats(Long problemId, Long submissionCount, Long acceptedCount) {

    /** ACCEPTED 비율(%) - 소수점은 버린다(15/40 → 37). 채점된 제출이 0이면 null */
    public Integer acceptedRate() {
        if (submissionCount == null || submissionCount == 0) {
            return null;
        }
        return (int) (acceptedCount * 100 / submissionCount);
    }
}
