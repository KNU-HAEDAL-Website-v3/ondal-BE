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

    List<JudgeResult> findAllByAssignmentId(Long assignmentId);

    /** 기동 시 재큐잉 - 끝나지 않은 채점 */
    List<JudgeResult> findAllByStatusIn(Collection<JudgeStatus> statuses);

    @Modifying
    @Query("delete from JudgeResult r where r.assignmentId = :assignmentId")
    void deleteAllByAssignmentId(@Param("assignmentId") Long assignmentId);
}
