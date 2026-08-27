package kr.haedal.ondal.assignment.repository;

import kr.haedal.ondal.assignment.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    /** 목록 기본 정렬: 차시 오름차순 → 등록순. PostgreSQL은 ASC에서 NULL을 마지막에 두므로 차시 없는 과제가 뒤로 간다 */
    List<Assignment> findAllByCohortIdOrderBySessionNoAscCreatedAtAsc(Long cohortId);

    /** 하위 리소스 스코프 조회 규약 - 경로의 cohortId와 함께 조회, 불일치·부재는 404 (guide/design.md 4절) */
    Optional<Assignment> findByIdAndCohortId(Long id, Long cohortId);

    /** 자동 채번용 - 전역 최대 번호. 과제가 하나도 없으면 empty (서비스가 1000으로 시작) */
    @Query("select max(a.problemNo) from Assignment a")
    Optional<Integer> findMaxProblemNo();

    /** 수동 지정(#15) 중복 검사 - 409 선반환용 */
    boolean existsByProblemNo(Integer problemNo);

    /** 번호 수정(#16) 중복 검사 - 자기 자신은 제외 */
    boolean existsByProblemNoAndIdNot(Integer problemNo, Long id);
}
