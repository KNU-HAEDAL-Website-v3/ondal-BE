package kr.haedal.ondal.submission.repository;

import kr.haedal.ondal.submission.dto.AssignmentSubmissionCount;
import kr.haedal.ondal.submission.dto.SubmissionMoment;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    /** 내 제출 이력 - 최신이 대표(맨 앞). idx_submissions_assignment_user가 이 조회를 지원. 응답에 링크가 실리므로 links를 fetch join(행별 N+1 방지) */
    @Query("""
            select s from Submission s left join fetch s.links
            where s.assignment.id = :assignmentId and s.user.id = :userId
            order by s.submittedAt desc""")
    List<Submission> findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(@Param("assignmentId") Long assignmentId,
                                                                          @Param("userId") Long userId);

    /** 손자 리소스 스코프 조회 - 경로의 assignmentId와 함께 조회, 불일치·부재는 404. 응답에 제출자·링크가 실리므로 fetch join */
    @Query("""
            select s from Submission s join fetch s.user left join fetch s.links
            where s.id = :id and s.assignment.id = :assignmentId""")
    Optional<Submission> findByIdAndAssignmentIdWithUser(@Param("id") Long id, @Param("assignmentId") Long assignmentId);

    /** 과제 삭제 연쇄용 - storedPath를 알아야 디스크 파일을 먼저 지울 수 있다 */
    List<Submission> findAllByAssignmentId(Long assignmentId);

    /** 재채점 대상·건수 - 이 과제의 CODE 제출 (judge 슬라이스) */
    List<Submission> findAllByAssignmentIdAndType(Long assignmentId, SubmissionType type);

    long countByAssignmentIdAndType(Long assignmentId, SubmissionType type);

    /** 현황판용 - 한 과제의 전체 제출 시각 (userId 기준 그룹핑은 서비스에서) */
    @Query("""
            select new kr.haedal.ondal.submission.dto.SubmissionMoment(s.id, s.assignment.id, s.user.id, s.submittedAt,
                case when s.mentorComment is null then false else true end)
            from Submission s where s.assignment.id = :assignmentId""")
    List<SubmissionMoment> findMomentsByAssignmentId(@Param("assignmentId") Long assignmentId);

    /** 과제 목록의 myStatus 조립용 - 요청자의 제출 시각을 과제 여러 개에 대해 쿼리 1번으로 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.SubmissionMoment(s.id, s.assignment.id, s.user.id, s.submittedAt,
                case when s.mentorComment is null then false else true end)
            from Submission s where s.assignment.id in :assignmentIds and s.user.id = :userId""")
    List<SubmissionMoment> findMomentsByAssignmentIdInAndUserId(@Param("assignmentIds") Collection<Long> assignmentIds,
                                                                @Param("userId") Long userId);

    // ---- HOJ 연습 제출 (V7) - 과제가 아니라 문제를 직접 가리키는 제출 -----------------------------------

    /** 내 연습 제출 이력 - 최신이 앞. 연습은 코드만이라 links 를 fetch 할 필요가 없다 */
    List<Submission> findAllByProblemIdAndUserIdOrderBySubmittedAtDesc(Long problemId, Long userId);

    /** 연습 제출 단건 - 본인 것만 열람한다(서비스가 user 로 좁힌다) */
    Optional<Submission> findByIdAndProblemIdAndUserId(Long id, Long problemId, Long userId);

    boolean existsByProblemId(Long problemId);

    /** 마이페이지 활동 요약 (MyStatsService) - 본인 제출 건수를 과제/연습으로 나눠 센다 (재제출 포함) */
    long countByUserIdAndAssignmentIsNotNull(Long userId);

    long countByUserIdAndProblemIsNotNull(Long userId);

    /**
     * 이 문제로 채점되는 제출 전부 - 과제로 낸 것(assignment.problem)과 HOJ 연습(problem) 양쪽.
     * 테스트케이스를 고치면 이 모두가 재채점 대상이다 - 채점 기준은 문제 하나를 공유하기 때문.
     */
    @Query("""
            select s from Submission s
            where s.type = :type and (s.problem.id = :problemId or s.assignment.problem.id = :problemId)""")
    List<Submission> findAllTargetingProblem(@Param("problemId") Long problemId, @Param("type") SubmissionType type);

    @Query("""
            select count(s) from Submission s
            where s.type = :type and (s.problem.id = :problemId or s.assignment.problem.id = :problemId)""")
    long countTargetingProblem(@Param("problemId") Long problemId, @Param("type") SubmissionType type);

    /** 과제 목록의 submissionCount(운영진 전용) 조립용 집계 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.AssignmentSubmissionCount(s.assignment.id, count(s))
            from Submission s where s.assignment.id in :assignmentIds group by s.assignment.id""")
    List<AssignmentSubmissionCount> countGroupedByAssignmentIdIn(@Param("assignmentIds") Collection<Long> assignmentIds);
}
