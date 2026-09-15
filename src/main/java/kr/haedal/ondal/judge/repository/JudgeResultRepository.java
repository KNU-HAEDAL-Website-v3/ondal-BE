package kr.haedal.ondal.judge.repository;

import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.JudgeStatus;
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
