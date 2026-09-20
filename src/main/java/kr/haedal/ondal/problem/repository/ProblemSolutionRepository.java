package kr.haedal.ondal.problem.repository;

import kr.haedal.ondal.problem.entity.ProblemSolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemSolutionRepository extends JpaRepository<ProblemSolution, Long> {

    /** 정답 코드 목록(운영진) - 응답에 저장한 사람이 실리므로 updatedBy 를 fetch join. 언어 이름순 */
    @Query("select s from ProblemSolution s join fetch s.updatedBy where s.problem.id = :problemId order by s.language asc")
    List<ProblemSolution> findAllByProblemIdWithUpdatedBy(@Param("problemId") Long problemId);

    /** 상세의 solutionLanguages(운영진에게만) - 코드 본문은 싣지 않는다 */
    @Query("select s.language from ProblemSolution s where s.problem.id = :problemId order by s.language asc")
    List<String> findLanguagesByProblemId(@Param("problemId") Long problemId);

    /** 통째 교체·문제 삭제 연쇄용 - 벌크 삭제. 호출 뒤 같은 트랜잭션에서 새 행을 저장해도 된다(영속성 컨텍스트에 남은 행이 없다는 전제) */
    @Modifying
    @Query("delete from ProblemSolution s where s.problem.id = :problemId")
    void deleteAllByProblemId(@Param("problemId") Long problemId);
}
