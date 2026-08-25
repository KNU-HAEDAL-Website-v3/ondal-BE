package kr.haedal.ondal.assignment.repository;

import kr.haedal.ondal.assignment.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /** 목록 기본 정렬: 차시 오름차순 → 등록순. PostgreSQL은 ASC에서 NULL을 마지막에 두므로 차시 없는 과제가 뒤로 간다 */
    List<Assignment> findAllByCohortIdOrderBySessionNoAscCreatedAtAsc(Long cohortId);

    /** 하위 리소스 스코프 조회 규약 - 경로의 cohortId와 함께 조회, 불일치·부재는 404 (guide/design.md 4절) */
    Optional<Assignment> findByIdAndCohortId(Long id, Long cohortId);
}
