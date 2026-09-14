package kr.haedal.ondal.judge.repository;

import kr.haedal.ondal.judge.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findAllByAssignmentIdOrderByPositionAsc(Long assignmentId);

    List<TestCase> findAllByAssignmentIdAndIsPublicTrueOrderByPositionAsc(Long assignmentId);

    long countByAssignmentId(Long assignmentId);

    /** 과제 목록의 judgeEnabled 조립용 - 케이스가 하나라도 있는 과제 id 만 (쿼리 1번) */
    @Query("select distinct t.assignment.id from TestCase t where t.assignment.id in :assignmentIds")
    List<Long> findAssignmentIdsWithCases(@Param("assignmentIds") Collection<Long> assignmentIds);

    /** 통째 교체·과제 삭제 연쇄용 - 벌크 삭제. 호출 뒤 영속성 컨텍스트에 TestCase 가 남아 있지 않다는 전제(서비스가 조회 전에 부른다) */
    @Modifying
    @Query("delete from TestCase t where t.assignment.id = :assignmentId")
    void deleteAllByAssignmentId(@Param("assignmentId") Long assignmentId);
}
