package kr.haedal.ondal.assignment.service;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;

import kr.haedal.ondal.assignment.dto.AssignmentCreateRequest;
import kr.haedal.ondal.assignment.dto.AssignmentResponse;
import kr.haedal.ondal.assignment.dto.AssignmentUpdateRequest;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.judge.service.JudgeService;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.service.ProblemService;
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
 * - V7 이후 과제는 "문제를 분반에 배정한 것" - 제목·본문·번호·테스트케이스는 Problem 소관이라 여기서 다루지 않는다.
 *   문제 삭제는 배정이 남아 있으면 409 로 막히므로, 과제를 지우기 전에는 문제도 사라지지 않는다.
 */
@Service
@Transactional
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final CohortRepository cohortRepository;
    private final AssignmentResponseAssembler assembler;
    private final SubmissionService submissionService;
    private final JudgeService judgeService;
    private final ProblemService problemService;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             CohortRepository cohortRepository,
                             AssignmentResponseAssembler assembler,
                             SubmissionService submissionService,
                             JudgeService judgeService,
                             ProblemService problemService) {
        this.assignmentRepository = assignmentRepository;
        this.cohortRepository = cohortRepository;
        this.assembler = assembler;
        this.submissionService = submissionService;
        this.judgeService = judgeService;
        this.problemService = problemService;
    }

    /** 목록 - 차시 오름차순(차시 없음 마지막) → 등록순. 보관 분반도 열람은 유지된다 */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findAll(Long cohortId, User viewer) {
        requireCohort(cohortId);
        return assembler.toResponses(
                assignmentRepository.findAllByCohortIdWithProblem(cohortId), cohortId, viewer);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse findOne(Long cohortId, Long assignmentId, User viewer) {
        return assembler.toResponse(requireAssignment(cohortId, assignmentId), cohortId, viewer);
    }

    public AssignmentResponse create(Long cohortId, AssignmentCreateRequest request, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Problem problem = problemService.requireProblem(request.problemId());
        Assignment assignment = assignmentRepository.save(
                Assignment.create(cohort, problem, request.sessionNo(), request.dueAt()));
        return assembler.toResponse(assignment, cohortId, viewer);
    }

    public AssignmentResponse update(Long cohortId, Long assignmentId, AssignmentUpdateRequest request, User viewer) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        Problem problem = problemService.requireProblem(request.problemId());
        assignment.update(problem, request.sessionNo(), request.dueAt());
        return assembler.toResponse(assignment, cohortId, viewer);
    }

    /** 삭제 = 연쇄 - 제출 파일·이력까지 함께 지운다. FE는 submissionCount로 "제출물 N건 삭제" 경고를 먼저 띄운다 */
    public void delete(Long cohortId, Long assignmentId) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        judgeService.deleteAllOf(assignment.getId());        // judge_results(제출 FK) → test_cases: 제출보다 먼저 (judge/design.md 결정 16)
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
