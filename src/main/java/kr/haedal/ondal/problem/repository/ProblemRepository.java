package kr.haedal.ondal.problem.repository;

import kr.haedal.ondal.problem.dto.TagProblemCount;
import kr.haedal.ondal.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    /** 라이브러리 기본 정렬 = 문제 번호 오름차순 - 사람이 "몇 번 문제" 로 기억한다 */
    @Query("select distinct p from Problem p left join fetch p.tags order by p.problemNo asc")
    List<Problem> findAllWithTags();

    /** 태그로 좁히기 - 고른 태그를 모두 가진 문제(AND). having count 로 교집합을 만든다 */
    @Query("""
            select p.id from Problem p join p.tags t
            where t.id in :tagIds
            group by p.id
            having count(distinct t.id) = :tagCount
            """)
    List<Long> findIdsHavingAllTags(@Param("tagIds") Collection<Long> tagIds, @Param("tagCount") long tagCount);

    @Query("select distinct p from Problem p left join fetch p.tags where p.id in :ids order by p.problemNo asc")
    List<Problem> findAllWithTagsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select distinct p from Problem p left join fetch p.tags where p.id = :id")
    Optional<Problem> findWithTagsById(@Param("id") Long id);

    Optional<Problem> findByProblemNo(Integer problemNo);

    /** 자동 채번용 - 전역 최대 번호. 문제가 하나도 없으면 empty (서비스가 1000으로 시작) */
    @Query("select max(p.problemNo) from Problem p")
    Optional<Integer> findMaxProblemNo();

    boolean existsByProblemNo(Integer problemNo);

    boolean existsByProblemNoAndIdNot(Integer problemNo, Long id);

    /** 태그 삭제 전 사용처 확인 - 이 태그를 쓰는 문제가 있는가 */
    @Query("select count(p) from Problem p join p.tags t where t.id = :tagId")
    long countByTagId(@Param("tagId") Long tagId);

    /** 사용자 페이지 태그 숙련도의 분모 - 태그별 문제 수, 이름순. 문제가 없는 태그는 행이 없다 (HOJ P3) */
    @Query("""
            select new kr.haedal.ondal.problem.dto.TagProblemCount(t.id, t.name, count(p))
            from Problem p join p.tags t group by t.id, t.name order by t.name asc""")
    List<TagProblemCount> countGroupedByTag();
}
