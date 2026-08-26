package kr.haedal.ondal.submission.repository;

import kr.haedal.ondal.submission.dto.AssignmentSubmissionCount;
import kr.haedal.ondal.submission.dto.SubmissionMoment;
import kr.haedal.ondal.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** 내 제출 이력 - 최신이 대표(맨 앞). idx_submissions_assignment_user가 이 조회를 지원 */
    List<Submission> findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(Long assignmentId, Long userId);

    /** 손자 리소스 스코프 조회 - 경로의 assignmentId와 함께 조회, 불일치·부재는 404. 응답에 제출자가 실리므로 user를 fetch join */
    @Query("select s from Submission s join fetch s.user where s.id = :id and s.assignment.id = :assignmentId")
    Optional<Submission> findByIdAndAssignmentIdWithUser(@Param("id") Long id, @Param("assignmentId") Long assignmentId);

    /** 과제 삭제 연쇄용 - storedPath를 알아야 디스크 파일을 먼저 지울 수 있다 */
    List<Submission> findAllByAssignmentId(Long assignmentId);

    /** 현황판용 - 한 과제의 전체 제출 시각 (userId 기준 그룹핑은 서비스에서) */
    @Query("""
            select new kr.haedal.ondal.submission.dto.SubmissionMoment(s.assignment.id, s.user.id, s.submittedAt)
            from Submission s where s.assignment.id = :assignmentId""")
    List<SubmissionMoment> findMomentsByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** 과제 목록의 myStatus 조립용 - 요청자의 제출 시각을 과제 여러 개에 대해 쿼리 1번으로 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.SubmissionMoment(s.assignment.id, s.user.id, s.submittedAt)
            from Submission s where s.assignment.id in :assignmentIds and s.user.id = :userId""")
    List<SubmissionMoment> findMomentsByAssignmentIdInAndUserId(@Param("assignmentIds") Collection<Long> assignmentIds,
                                                                @Param("userId") Long userId);

    /** 과제 목록의 submissionCount(운영진 전용) 조립용 집계 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.AssignmentSubmissionCount(s.assignment.id, count(s))
            from Submission s where s.assignment.id in :assignmentIds group by s.assignment.id""")
    List<AssignmentSubmissionCount> countGroupedByAssignmentIdIn(@Param("assignmentIds") Collection<Long> assignmentIds);
}
