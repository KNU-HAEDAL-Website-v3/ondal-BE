package kr.haedal.ondal.judge.repository;

import kr.haedal.ondal.judge.dto.SolvedProblemRow;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.JudgeStatus;
import kr.haedal.ondal.problem.dto.ProblemJudgeStats;
import kr.haedal.ondal.problem.dto.ProblemSolvedUserCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JudgeResultRepository extends JpaRepository<JudgeResult, Long> {

    /** 이력·현황판 조립용 - 제출 id 묶음의 결과를 쿼리 1번으로 */
    List<JudgeResult> findAllBySubmissionIdIn(Collection<Long> submissionIds);

    /** 과제 단위 재채점·삭제 연쇄용 - 그 과제에 달린 제출들의 채점 결과 (V7: judge_results 는 문제 기준이라 제출로 거슬러 찾는다) */
    @Query("""
            select r from JudgeResult r, Submission s
            where s.id = r.submissionId and s.assignment.id = :assignmentId""")
    List<JudgeResult> findAllByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** 내가 맞힌 문제 - 과제 제출·HOJ 연습 제출 어느 쪽이든 ACCEPTED 가 하나라도 있으면 해결 */
    @Query("""
            select distinct r.problemId from JudgeResult r, Submission s
            where s.id = r.submissionId and s.user.id = :userId
              and r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED""")
    List<Long> findSolvedProblemIdsByUserId(@Param("userId") Long userId);

    /** 시도한 문제 - 채점된 제출(judge_results 행)이 하나라도 있는 문제. 푼 문제를 빼면 "시도 중" (HOJ P3) */
    @Query("""
            select distinct r.problemId from JudgeResult r, Submission s
            where s.id = r.submissionId and s.user.id = :userId""")
    List<Long> findAttemptedProblemIdsByUserId(@Param("userId") Long userId);

    /** 다른 사람 풀이 열람 조건 - 이 문제를 맞힌 적이 있는가 (연습·과제 무관) */
    @Query("""
            select count(r) from JudgeResult r, Submission s
            where s.id = r.submissionId and s.user.id = :userId and r.problemId = :problemId
              and r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED""")
    long countAcceptedByUserIdAndProblemId(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /** 사용자 페이지 - 연습 제출 중 ACCEPTED 건수 (재제출 포함) */
    @Query("""
            select count(r) from JudgeResult r, Submission s
            where s.id = r.submissionId and s.user.id = :userId and s.problem is not null
              and r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED""")
    long countPracticeAcceptedByUserId(@Param("userId") Long userId);

    /** 목록·상세의 문제별 통계 조립용 - 채점된 제출 수(judge_results 행 수)·ACCEPTED 수를 문제별로 (쿼리 1번, 연습·과제 합산) */
    @Query("""
            select new kr.haedal.ondal.problem.dto.ProblemJudgeStats(r.problemId, count(r),
                sum(case when r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED then 1L else 0L end))
            from JudgeResult r where r.problemId in :problemIds group by r.problemId""")
    List<ProblemJudgeStats> countGroupedByProblemIdIn(@Param("problemIds") Collection<Long> problemIds);

    /** 목록·상세의 "푼 사람 수" - ACCEPTED 판정이 있는 사용자 수를 문제별로 (쿼리 1번, 연습·과제 합산) */
    @Query("""
            select new kr.haedal.ondal.problem.dto.ProblemSolvedUserCount(r.problemId, count(distinct s.user.id))
            from JudgeResult r, Submission s
            where s.id = r.submissionId and r.problemId in :problemIds
              and r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED
            group by r.problemId""")
    List<ProblemSolvedUserCount> countSolvedUsersGroupedByProblemIdIn(@Param("problemIds") Collection<Long> problemIds);

    /**
     * 랭킹 원자료 - (사용자, 문제)마다 처음 맞힌 제출 시각. 연습·과제 합산, 못 푼 문제는 행이 없다.
     * 사용자별 합산(푼 문제 수·마지막 정답 시각)은 HojRankingService 가 이 행들로 센다 - 파생 테이블 없이 JPQL 로 끝내기 위해
     */
    @Query("""
            select new kr.haedal.ondal.judge.dto.SolvedProblemRow(s.user.id, r.problemId, min(s.submittedAt))
            from JudgeResult r, Submission s
            where s.id = r.submissionId and r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED
            group by s.user.id, r.problemId""")
    List<SolvedProblemRow> findSolvedProblemRows();

    /** 기동 시 재큐잉 - 끝나지 않은 채점 */
    List<JudgeResult> findAllByStatusIn(Collection<JudgeStatus> statuses);

    /** 과제 삭제 연쇄 - 그 과제의 제출에 달린 결과만 지운다(같은 문제의 다른 배정·연습 제출 결과는 남는다) */
    @Modifying
    @Query("""
            delete from JudgeResult r where r.submissionId in
            (select s.id from Submission s where s.assignment.id = :assignmentId)""")
    void deleteAllByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** 문제 삭제 연쇄 - 그 문제의 모든 채점 결과 */
    @Modifying
    @Query("delete from JudgeResult r where r.problemId = :problemId")
    void deleteAllByProblemId(@Param("problemId") Long problemId);
}
