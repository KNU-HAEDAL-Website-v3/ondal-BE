package kr.haedal.ondal.assignment.service;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;

import kr.haedal.ondal.assignment.dto.AssignmentCreateRequest;
import kr.haedal.ondal.assignment.dto.AssignmentResponse;
import kr.haedal.ondal.assignment.dto.AssignmentUpdateRequest;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 과제 CRUD - 하위 리소스 스코프 조회 규약의 첫 적용 사례.
 * - 하위 id 조회는 반드시 findByIdAndCohortId - 경로의 cohortId와 불일치(다른 반 과제)·부재면 404 (존재 비노출)
 * - 쓰기는 첫 줄에서 cohort.ensureActive() - 보관 분반이면 409
 * - 의존은 assignment → cohort 단방향 (enrollment 선례)
 */
@Service
@Transactional
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CohortRepository cohortRepository;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             CohortRepository cohortRepository) {
        this.assignmentRepository = assignmentRepository;
        this.cohortRepository = cohortRepository;
    }

    /** 목록 - 차시 오름차순(차시 없음 마지막) → 등록순. 보관 분반도 열람은 유지된다 */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findAll(Long cohortId) {
        requireCohort(cohortId);
        return assignmentRepository.findAllByCohortIdOrderBySessionNoAscCreatedAtAsc(cohortId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse findOne(Long cohortId, Long assignmentId) {
        return AssignmentResponse.from(requireAssignment(cohortId, assignmentId));
    }

    public AssignmentResponse create(Long cohortId, AssignmentCreateRequest request) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Assignment assignment = assignmentRepository.save(Assignment.create(
                cohort, request.sessionNo(), request.title(), request.description(), request.dueAt()));
        return AssignmentResponse.from(assignment);
    }

    public AssignmentResponse update(Long cohortId, Long assignmentId, AssignmentUpdateRequest request) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        assignment.update(request.sessionNo(), request.title(), request.description(), request.dueAt());
        return AssignmentResponse.from(assignment);
    }

    /** 삭제 - 이 슬라이스는 과제 행만 지운다. 제출물 연쇄 삭제(경고 건수 포함)는 제출 슬라이스에서 (schema.md 4절) */
    public void delete(Long cohortId, Long assignmentId) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        assignmentRepository.delete(assignment);
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }

    private Assignment requireAssignment(Long cohortId, Long assignmentId) {
        return assignmentRepository.findByIdAndCohortId(assignmentId, cohortId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
    }
}
