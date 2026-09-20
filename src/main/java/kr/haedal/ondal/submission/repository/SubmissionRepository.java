package kr.haedal.ondal.submission.repository;

import kr.haedal.ondal.judge.entity.Verdict;
import kr.haedal.ondal.submission.dto.AssignmentSubmissionCount;
import kr.haedal.ondal.submission.dto.LanguageCount;
import kr.haedal.ondal.submission.dto.SubmissionMoment;
import kr.haedal.ondal.submission.dto.UserSubmissionCount;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    // ---- HOJ P3 (docs hoj/api.md) - 채점 현황 피드·사용자 페이지·랭킹·다른 사람 풀이 ------------------------------

    /**
     * 채점 현황 피드 - 연습 제출만, 최신(id) 먼저. 필터는 null 이면 무시, beforeId 는 커서(그보다 작은 id 만).
     * 응답에 문제·제출자가 실리므로 fetch join. 판정은 judge_results 가 연관이 아니라 exists 로 건다. 페이지 크기는 Pageable 로
     */
    @Query("""
            select s from Submission s join fetch s.problem join fetch s.user
            where s.problem is not null
              and (:problemId is null or s.problem.id = :problemId)
              and (:userId is null or s.user.id = :userId)
              and (:language is null or s.language = :language)
              and (:beforeId is null or s.id < :beforeId)
              and (:verdict is null or exists (select r from JudgeResult r where r.submissionId = s.id and r.verdict = :verdict))
            order by s.id desc""")
    List<Submission> findPracticeFeed(@Param("problemId") Long problemId, @Param("userId") Long userId,
                                      @Param("language") String language, @Param("verdict") Verdict verdict,
                                      @Param("beforeId") Long beforeId, Pageable pageable);

    /**
     * 다른 사람 풀이 - 이 문제의 연습 제출 중 ACCEPTED 를 사용자당 최신 1건(id 최대), 요청자 본인 제외, 최신 먼저.
     * language 는 null 이면 전체. 응답에 제출자가 실리므로 fetch join. 건수 상한은 Pageable 로
     */
    @Query("""
            select s from Submission s join fetch s.user
            where s.id in (
                select max(s2.id) from Submission s2, JudgeResult r
                where r.submissionId = s2.id and s2.problem.id = :problemId
                  and r.verdict = kr.haedal.ondal.judge.entity.Verdict.ACCEPTED
                  and s2.user.id <> :viewerId
                  and (:language is null or s2.language = :language)
                group by s2.user.id)
            order by s.submittedAt desc""")
    List<Submission> findLatestAcceptedPerUser(@Param("problemId") Long problemId, @Param("viewerId") Long viewerId,
                                               @Param("language") String language, Pageable pageable);

    /** 사용자 페이지 - 언어별 연습 제출 수, 많이 쓴 언어 먼저 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.LanguageCount(s.language, count(s))
            from Submission s where s.user.id = :userId and s.problem is not null
            group by s.language order by count(s) desc, s.language asc""")
    List<LanguageCount> countPracticeGroupedByLanguage(@Param("userId") Long userId);

    /** 랭킹 행의 submissionCount - 사용자 묶음의 연습 제출 수를 쿼리 1번으로 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.UserSubmissionCount(s.user.id, count(s))
            from Submission s where s.problem is not null and s.user.id in :userIds group by s.user.id""")
    List<UserSubmissionCount> countPracticeGroupedByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /**
     * 활동 잔디 - since 이후 연습 제출 수를 KST 날짜별로. 제출 0인 날은 행이 없다. 행 = [날짜 문자열(YYYY-MM-DD), 건수].
     * 날짜 경계가 KST 여야 해서(저장은 UTC) 네이티브 SQL - at time zone 은 JPQL 에 없다
     */
    @Query(value = """
            select to_char(cast((s.submitted_at at time zone 'Asia/Seoul') as date), 'YYYY-MM-DD') as day, count(*) as cnt
            from submissions s
            where s.user_id = :userId and s.problem_id is not null and s.submitted_at >= :since
            group by day order by day asc""", nativeQuery = true)
    List<Object[]> countPracticeGroupedByKstDay(@Param("userId") Long userId, @Param("since") Instant since);

    /** 과제 목록의 submissionCount(운영진 전용) 조립용 집계 */
    @Query("""
            select new kr.haedal.ondal.submission.dto.AssignmentSubmissionCount(s.assignment.id, count(s))
            from Submission s where s.assignment.id in :assignmentIds group by s.assignment.id""")
    List<AssignmentSubmissionCount> countGroupedByAssignmentIdIn(@Param("assignmentIds") Collection<Long> assignmentIds);
}
