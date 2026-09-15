package kr.haedal.ondal.problem.repository;

import kr.haedal.ondal.problem.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** 목록·선택지 정렬은 이름순 - 사람이 찾는 순서 */
    List<Tag> findAllByOrderByNameAsc();

    List<Tag> findAllByIdIn(Collection<Long> ids);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
