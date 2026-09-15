package kr.haedal.ondal.assignment.repository;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.problem.dto.ProblemAssignedCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /** 목록 기본 정렬: 차시 오름차순 → 등록순. PostgreSQL은 ASC에서 NULL을 마지막에 두므로 차시 없는 과제가 뒤로 간다 */
    @Query("""
            select a from Assignment a join fetch a.problem
            where a.cohort.id = :cohortId order by a.sessionNo asc, a.createdAt asc""")
    List<Assignment> findAllByCohortIdWithProblem(@Param("cohortId") Long cohortId);

    /** 하위 리소스 스코프 조회 규약 - 경로의 cohortId와 함께 조회, 불일치·부재는 404 (guide/design.md 4절) */
    @Query("select a from Assignment a join fetch a.problem where a.id = :id and a.cohort.id = :cohortId")
    Optional<Assignment> findByIdAndCohortId(@Param("id") Long id, @Param("cohortId") Long cohortId);

    /** 문제 목록의 assignedCount 조립 + 삭제 가능 여부 판정 (V7) */
    @Query("""
            select new kr.haedal.ondal.problem.dto.ProblemAssignedCount(a.problem.id, count(a))
            from Assignment a where a.problem.id in :problemIds group by a.problem.id""")
    List<ProblemAssignedCount> countGroupedByProblemIdIn(@Param("problemIds") Collection<Long> problemIds);

    boolean existsByProblemId(Long problemId);
}
