package kr.haedal.ondal.assignment.service;

import kr.haedal.ondal.assignment.dto.AssignmentResponse;
import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.submission.dto.AssignmentSubmissionCount;
import kr.haedal.ondal.submission.dto.SubmissionMoment;
import kr.haedal.ondal.submission.entity.SubmissionStatus;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assignment 목록 → AssignmentResponse 목록. 요청자에 따라 달라지는 필드(myStatus·submissionCount)를 채운다.
 * CohortResponseAssembler 패턴 복제 - 과제 N개에 대해 제출 조회는 쿼리 2번(내 시각·건수 집계)으로 끝낸다.
 * 호출하는 쪽의 트랜잭션 안에서 실행된다는 전제 - 자체 @Transactional은 없다.
 *
 * - myStatus: 분반 소속자만(수강생·운영진). 비소속 관리자는 null - 제출할 일이 없는 사람의 상태는 무의미
 * - submissionCount: 운영진·관리자만 값. 수강생은 null - 타인의 제출 여부가 유추되는 집계는 학생에게 비노출 (studentCount 선례)
 */
@Component
public class AssignmentResponseAssembler {

    private final SubmissionRepository submissionRepository;
    private final EnrollmentRepository enrollmentRepository;

    public AssignmentResponseAssembler(SubmissionRepository submissionRepository,
                                       EnrollmentRepository enrollmentRepository) {
        this.submissionRepository = submissionRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public AssignmentResponse toResponse(Assignment assignment, Long cohortId, User viewer) {
        return toResponses(List.of(assignment), cohortId, viewer).get(0);
    }

    public List<AssignmentResponse> toResponses(List<Assignment> assignments, Long cohortId, User viewer) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> assignmentIds = assignments.stream().map(Assignment::getId).toList();

        EnrollmentRole myRole = enrollmentRepository.findByCohortIdAndUserId(cohortId, viewer.getId())
                .map(Enrollment::getRole)
                .orElse(null);

        Map<Long, List<Instant>> mySubmittedAts = myRole == null
                ? Map.of()
                : submissionRepository.findMomentsByAssignmentIdInAndUserId(assignmentIds, viewer.getId()).stream()
                        .collect(Collectors.groupingBy(SubmissionMoment::assignmentId,
                                Collectors.mapping(SubmissionMoment::submittedAt, Collectors.toList())));

        boolean canSeeCount = viewer.isAdmin() || myRole == EnrollmentRole.OPERATOR;
        Map<Long, Long> counts = canSeeCount
                ? submissionRepository.countGroupedByAssignmentIdIn(assignmentIds).stream()
                        .collect(Collectors.toMap(AssignmentSubmissionCount::assignmentId, AssignmentSubmissionCount::count))
                : Map.of();

        return assignments.stream()
                .map(assignment -> AssignmentResponse.of(
                        assignment,
                        myRole == null ? null : SubmissionStatus.from(
                                mySubmittedAts.getOrDefault(assignment.getId(), List.of()), assignment.getDueAt()),
                        canSeeCount ? counts.getOrDefault(assignment.getId(), 0L).intValue() : null))
                .toList();
    }
}
