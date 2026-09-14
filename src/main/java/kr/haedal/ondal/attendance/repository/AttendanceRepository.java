package kr.haedal.ondal.attendance.repository;

import kr.haedal.ondal.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** 한 차시의 기록 - 명부 조립용. 응답에 사용자가 실리므로 user 를 fetch join (WithXxx + @Query 규약) */
    @Query("select a from Attendance a join fetch a.user where a.session.id = :sessionId")
    List<Attendance> findAllBySessionIdWithUser(@Param("sessionId") Long sessionId);

    /** 분반 전체 기록 - 학생별 누계(출석률) 계산용. 차시별 그룹이 필요하므로 session 도 함께 */
    @Query("select a from Attendance a join fetch a.session where a.session.cohort.id = :cohortId")
    List<Attendance> findAllByCohortIdWithSession(@Param("cohortId") Long cohortId);

    /** 한 학생의 분반 기록 - 내 출석(#40) */
    @Query("select a from Attendance a join fetch a.session where a.session.cohort.id = :cohortId and a.user.id = :userId")
    List<Attendance> findAllByCohortIdAndUserIdWithSession(@Param("cohortId") Long cohortId, @Param("userId") Long userId);

    long countBySessionId(Long sessionId);

    /** 차시 삭제의 서비스 연쇄 (docs attendance/design.md 결정 9) */
    @Modifying
    @Query("delete from Attendance a where a.session.id = :sessionId")
    void deleteAllBySessionId(@Param("sessionId") Long sessionId);
}
