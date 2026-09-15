package kr.haedal.ondal.judge.repository;

import kr.haedal.ondal.judge.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findAllByProblemIdOrderByPositionAsc(Long problemId);

    List<TestCase> findAllByProblemIdAndIsPublicTrueOrderByPositionAsc(Long problemId);

    long countByProblemId(Long problemId);

    /** 목록의 judgeEnabled 조립용 - 케이스가 하나라도 있는 문제 id 만 (쿼리 1번) */
    @Query("select distinct t.problem.id from TestCase t where t.problem.id in :problemIds")
    List<Long> findProblemIdsWithCases(@Param("problemIds") Collection<Long> problemIds);

    /** 통째 교체·문제 삭제 연쇄용 - 벌크 삭제. 호출 뒤 영속성 컨텍스트에 TestCase 가 남아 있지 않다는 전제(서비스가 조회 전에 부른다) */
    @Modifying
    @Query("delete from TestCase t where t.problem.id = :problemId")
    void deleteAllByProblemId(@Param("problemId") Long problemId);
}
