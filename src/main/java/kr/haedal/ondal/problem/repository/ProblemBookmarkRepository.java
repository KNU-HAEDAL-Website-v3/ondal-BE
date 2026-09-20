package kr.haedal.ondal.problem.repository;

import kr.haedal.ondal.problem.entity.ProblemBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemBookmarkRepository extends JpaRepository<ProblemBookmark, ProblemBookmark.Key> {

    boolean existsByUserIdAndProblemId(Long userId, Long problemId);

    /** 목록·상세의 bookmarked 조립용 - 내가 북마크한 문제 id 전부 (쿼리 1번) */
    @Query("select b.problemId from ProblemBookmark b where b.userId = :userId")
    List<Long> findProblemIdsByUserId(@Param("userId") Long userId);

    /** 북마크 해제 - 없으면 0건, 멱등 */
    @Modifying
    @Query("delete from ProblemBookmark b where b.userId = :userId and b.problemId = :problemId")
    int deleteByUserIdAndProblemId(@Param("userId") Long userId, @Param("problemId") Long problemId);

    /** 문제 삭제 연쇄 */
    @Modifying
    @Query("delete from ProblemBookmark b where b.problemId = :problemId")
    void deleteAllByProblemId(@Param("problemId") Long problemId);
}
