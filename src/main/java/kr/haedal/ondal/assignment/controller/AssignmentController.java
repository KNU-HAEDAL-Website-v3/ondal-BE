package kr.haedal.ondal.assignment.controller;

import kr.haedal.ondal.assignment.service.AssignmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.assignment.dto.AssignmentCreateRequest;
import kr.haedal.ondal.assignment.dto.AssignmentResponse;
import kr.haedal.ondal.assignment.dto.AssignmentUpdateRequest;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 과제 API (#13~#17) - 조회는 분반 소속 누구나, 등록·수정·삭제는 운영진 이상.
 * 메서드마다: 권한 어노테이션 → 검증(@Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 */
@Tag(name = "Assignment", description = "과제 - 조회는 분반 소속자, 등록·수정·삭제는 운영진 이상")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Operation(summary = "과제 목록 - 차시 오름차순(차시 없음 마지막), 같은 차시는 등록순. myStatus·submissionCount는 요청자 의존")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping
    public List<AssignmentResponse> list(@PathVariable Long cohortId, @LoginUser User user) {
        return assignmentService.findAll(cohortId, user);
    }

    @Operation(summary = "과제 상세")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/{assignmentId}")
    public AssignmentResponse get(@PathVariable Long cohortId, @PathVariable Long assignmentId, @LoginUser User user) {
        return assignmentService.findOne(cohortId, assignmentId, user);
    }

    @Operation(summary = "[운영진] 과제 등록. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PostMapping
    public ResponseEntity<AssignmentResponse> create(@PathVariable Long cohortId,
                                                     @RequestBody @Valid AssignmentCreateRequest request,
                                                     @LoginUser User user) {
        AssignmentResponse created = assignmentService.create(cohortId, request, user);
        // 계약은 본문의 id. Location은 REST 관례상 덧붙이는 것 (CORS exposedHeaders 없이는 브라우저에서 못 읽음)
        return ResponseEntity.created(URI.create("/api/cohorts/" + cohortId + "/assignments/" + created.id()))
                .body(created);
    }

    @Operation(summary = "[운영진] 과제 수정 (전체 교체). 마감을 바꾸면 지각 판정 재계산. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PutMapping("/{assignmentId}")
    public AssignmentResponse update(@PathVariable Long cohortId,
                                     @PathVariable Long assignmentId,
                                     @RequestBody @Valid AssignmentUpdateRequest request,
                                     @LoginUser User user) {
        return assignmentService.update(cohortId, assignmentId, request, user);
    }

    @Operation(summary = "[운영진] 과제 삭제 - 제출 이력·파일까지 연쇄 삭제. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long cohortId, @PathVariable Long assignmentId) {
        assignmentService.delete(cohortId, assignmentId);
    }
}
