package kr.haedal.hoj.enrollment.repository;

import kr.haedal.hoj.enrollment.entity.Enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 리포지토리 규약 (이후 도메인도 동일):
 * - 연관을 fetch join 하는 조회는 이름 끝에 WithXxx 를 붙이고 반드시 @Query 를 단다
 *   (@Query 없이 이 이름을 쓰면 Spring Data 가 'With' 를 속성으로 파싱해 기동 실패).
 * - enum 컬럼 order by 는 STRING 매핑이라 알파벳순 - 의도한 순서인지 주석으로 밝힌다.
 */
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 권한 판정용 - 연관은 안 건드리고 role만 본다 */
    Optional<Enrollment> findByCohortIdAndUserId(Long cohortId, Long userId);

    /** 내 분반 목록 - cohort를 fetch join 해서 트랜잭션 안에서 LAZY를 끝낸다 */
    @Query("select e from Enrollment e join fetch e.cohort where e.user.id = :userId")
    List<Enrollment> findAllByUserIdWithCohort(@Param("userId") Long userId);

    /** 분반 명부 - user를 fetch join. 운영진(OPERATOR)이 먼저('O' < 'S' 알파벳순), 그다음 등록순 */
    @Query("select e from Enrollment e join fetch e.user where e.cohort.id = :cohortId order by e.role asc, e.createdAt asc")
    List<Enrollment> findAllByCohortIdWithUser(@Param("cohortId") Long cohortId);

    /** 여러 분반의 응답(운영진 목록·수강생 수·내 역할)을 쿼리 1번으로 조립하기 위한 조회 */
    @Query("select e from Enrollment e join fetch e.user where e.cohort.id in :cohortIds")
    List<Enrollment> findAllByCohortIdInWithUser(@Param("cohortIds") Collection<Long> cohortIds);
}
