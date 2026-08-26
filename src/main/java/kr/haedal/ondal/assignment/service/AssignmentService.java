package kr.haedal.ondal.assignment.service;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;

import kr.haedal.ondal.assignment.dto.AssignmentCreateRequest;
import kr.haedal.ondal.assignment.dto.AssignmentResponse;
import kr.haedal.ondal.assignment.dto.AssignmentUpdateRequest;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.submission.service.SubmissionService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 과제 CRUD - 하위 리소스 스코프 조회 규약의 첫 적용 사례.
 * - 하위 id 조회는 반드시 findByIdAndCohortId - 경로의 cohortId와 불일치(다른 반 과제)·부재면 404 (존재 비노출)
 * - 쓰기는 첫 줄에서 cohort.ensureActive() - 보관 분반이면 409
 * - 응답 조립은 AssignmentResponseAssembler - myStatus·submissionCount가 요청자 의존이라 viewer를 받는다
 * - 삭제는 연쇄: 파일 → submissions(SubmissionService.deleteAllOf) → assignment. FK(RESTRICT)가 순서 누락의 안전망
 */
@Service
@Transactional
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CohortRepository cohortRepository;
    private final AssignmentResponseAssembler assembler;
    private final SubmissionService submissionService;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             CohortRepository cohortRepository,
                             AssignmentResponseAssembler assembler,
                             SubmissionService submissionService) {
        this.assignmentRepository = assignmentRepository;
        this.cohortRepository = cohortRepository;
        this.assembler = assembler;
        this.submissionService = submissionService;
    }

    /** 목록 - 차시 오름차순(차시 없음 마지막) → 등록순. 보관 분반도 열람은 유지된다 */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findAll(Long cohortId, User viewer) {
        requireCohort(cohortId);
        return assembler.toResponses(
                assignmentRepository.findAllByCohortIdOrderBySessionNoAscCreatedAtAsc(cohortId), cohortId, viewer);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse findOne(Long cohortId, Long assignmentId, User viewer) {
        return assembler.toResponse(requireAssignment(cohortId, assignmentId), cohortId, viewer);
    }

    public AssignmentResponse create(Long cohortId, AssignmentCreateRequest request, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Assignment assignment = assignmentRepository.save(Assignment.create(
                cohort, request.sessionNo(), request.title(), request.description(), request.dueAt()));
        return assembler.toResponse(assignment, cohortId, viewer);
    }

    public AssignmentResponse update(Long cohortId, Long assignmentId, AssignmentUpdateRequest request, User viewer) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        assignment.update(request.sessionNo(), request.title(), request.description(), request.dueAt());
        return assembler.toResponse(assignment, cohortId, viewer);
    }

    /** 삭제 = 연쇄 - 제출 파일·이력까지 함께 지운다. FE는 submissionCount로 "제출물 N건 삭제" 경고를 먼저 띄운다 */
    public void delete(Long cohortId, Long assignmentId) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        submissionService.deleteAllOf(assignment.getId());
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
