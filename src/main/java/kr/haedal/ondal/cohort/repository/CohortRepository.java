package kr.haedal.ondal.cohort.repository;

import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.entity.CohortStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findAllByStatusOrderByCreatedAtDesc(CohortStatus status);
}
