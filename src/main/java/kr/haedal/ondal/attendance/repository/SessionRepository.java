package kr.haedal.ondal.attendance.repository;

import kr.haedal.ondal.attendance.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /** 분반의 차시 - 날짜 → 번호 오름차순 (docs attendance/api.md #34) */
    List<Session> findAllByCohortIdOrderByHeldOnAscSessionNoAsc(Long cohortId);

    /** 하위 리소스 스코프 조회 규약 - 경로의 cohortId 와 함께 조회, 불일치·부재는 404 */
    Optional<Session> findByIdAndCohortId(Long id, Long cohortId);

    /** 자동 채번용 - 현재 최대 번호 (없으면 null). 동시 등록 충돌은 uk_sessions_cohort_no 가 최후 방어 → 409 */
    @Query("select max(s.sessionNo) from Session s where s.cohort.id = :cohortId")
    Integer findMaxSessionNo(@Param("cohortId") Long cohortId);

    boolean existsByCohortIdAndSessionNo(Long cohortId, Integer sessionNo);

    long countByCohortId(Long cohortId);
}
