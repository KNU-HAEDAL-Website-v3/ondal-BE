package kr.haedal.ondal.notice.repository;

import kr.haedal.ondal.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 관리자 목록 - 전부. 필독 먼저 → 최신순, 같은 시각은 id desc. 응답에 작성자·분반 이름이 실리므로 함께 fetch (WithXxx + @Query 규약) */
    @Query("select n from Notice n join fetch n.author left join fetch n.cohort "
            + "order by n.pinned desc, n.createdAt desc, n.id desc")
    List<Notice> findAllWithAuthor();

    /** 부원 목록 - 전체 공지 + 소속 분반(cohortIds) 공지. 빈 소속은 서비스가 존재하지 않는 id 하나로 바꿔 넘긴다 */
    @Query("select n from Notice n join fetch n.author left join fetch n.cohort "
            + "where n.cohort is null or n.cohort.id in :cohortIds "
            + "order by n.pinned desc, n.createdAt desc, n.id desc")
    List<Notice> findAllVisibleWithAuthor(@Param("cohortIds") Collection<Long> cohortIds);

    @Query("select n from Notice n join fetch n.author left join fetch n.cohort where n.id = :id")
    Optional<Notice> findByIdWithAuthor(@Param("id") Long id);
}
