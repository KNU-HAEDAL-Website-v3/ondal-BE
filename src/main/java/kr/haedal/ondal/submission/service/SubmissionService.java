package kr.haedal.ondal.submission.service;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.submission.dto.StatusBoardRow;
import kr.haedal.ondal.submission.dto.SubmissionCreateRequest;
import kr.haedal.ondal.submission.dto.SubmissionFile;
import kr.haedal.ondal.submission.dto.SubmissionMoment;
import kr.haedal.ondal.submission.dto.SubmissionResponse;
import kr.haedal.ondal.submission.dto.SubmissionSummary;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionStatus;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 제출 - append-only 이력. 수정·삭제 서비스 메서드가 없다(재제출이 곧 수정).
 * - 지각 판정 시각 = 서버 수신 시각(Submission.create의 Instant.now()) - 클라이언트 시계 불신
 * - 손자 리소스라 스코프 체인 2단: assignment는 (id, cohortId), submission은 (id, assignmentId)
 * - 학생은 자기 제출만 - 타인 제출물 접근은 403이 아니라 404 (존재 비노출, guide 결정 11)
 * - 열람(#19~#22)은 보관 분반에서도 유지 - ensureActive는 제출(#18)에만
 */
@Service
@Transactional
public class SubmissionService {

    static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB - Spring multipart 한도와 이중 (MockMvc는 서블릿 한도를 안 태우므로 서비스 검증이 실효 방어)

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FileStorage fileStorage;

    public SubmissionService(SubmissionRepository submissionRepository,
                             AssignmentRepository assignmentRepository,
                             CohortRepository cohortRepository,
                             EnrollmentRepository enrollmentRepository,
                             FileStorage fileStorage) {
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.fileStorage = fileStorage;
    }

    /** #18 제출 - 운영진·관리자도 가능(현황판 명단은 STUDENT만이라 통계를 오염하지 않는다) */
    public SubmissionResponse create(Long cohortId, Long assignmentId, User submitter,
                                     SubmissionCreateRequest request, MultipartFile file) {
        requireCohort(cohortId).ensureActive();
        Assignment assignment = requireAssignment(cohortId, assignmentId);

        String codeText = normalize(request.codeText());
        String language = normalize(request.language());
        String linkUrl = normalize(request.linkUrl());
        boolean hasFile = file != null && !file.isEmpty();
        validate(codeText, language, linkUrl, hasFile, file);

        // 순서: 검증 → 디스크 저장 → DB insert. DB가 실패하면 방금 저장한 파일을 지워 고아 파일을 막는다
        String storedPath = hasFile ? fileStorage.store(file) : null;
        Submission submission;
        try {
            submission = submissionRepository.save(Submission.create(
                    assignment, submitter, codeText, language,
                    hasFile ? file.getOriginalFilename() : null, storedPath,
                    hasFile ? file.getSize() : null, linkUrl));
        } catch (RuntimeException e) {
            if (storedPath != null) {
                fileStorage.delete(storedPath);
            }
            throw e;
        }
        return SubmissionResponse.of(submission, assignment.getDueAt(), summaryOf(cohortId, submitter));
    }

    /** #19 내 제출 이력 - 최신순. 코드 전문은 싣지 않는다 */
    @Transactional(readOnly = true)
    public List<SubmissionSummary> findMy(Long cohortId, Long assignmentId, User viewer) {
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        return submissionRepository.findAllByAssignmentIdAndUserIdOrderBySubmittedAtDesc(assignmentId, viewer.getId()).stream()
                .map(submission -> SubmissionSummary.of(submission, assignment.getDueAt()))
                .toList();
    }

    /** #20 제출 상세 - 본인 또는 운영진 이상. 타인 것은 404 */
    @Transactional(readOnly = true)
    public SubmissionResponse findOne(Long cohortId, Long assignmentId, Long submissionId, User viewer) {
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        Submission submission = requireViewable(cohortId, assignmentId, submissionId, viewer);
        return SubmissionResponse.of(submission, assignment.getDueAt(), summaryOf(cohortId, submission.getUser()));
    }

    /** #21 파일 다운로드 - 권한은 #20과 동일. 파일 없는 제출(코드·링크)은 404 */
    @Transactional(readOnly = true)
    public SubmissionFile loadFile(Long cohortId, Long assignmentId, Long submissionId, User viewer) {
        requireAssignment(cohortId, assignmentId);
        Submission submission = requireViewable(cohortId, assignmentId, submissionId, viewer);
        if (!submission.hasFile()) {
            throw new NotFoundException("제출 파일이 없습니다.");
        }
        return new SubmissionFile(fileStorage.load(submission.getStoredPath()), submission.getFileName());
    }

    /** #22 현황판 - 행 = 현재 STUDENT 명단(이름순), 미제출자 포함. 운영진 제출은 행에 없다 */
    @Transactional(readOnly = true)
    public List<StatusBoardRow> statusBoard(Long cohortId, Long assignmentId) {
        Assignment assignment = requireAssignment(cohortId, assignmentId);
        Map<Long, List<SubmissionMoment>> momentsByUser = submissionRepository.findMomentsByAssignmentId(assignmentId).stream()
                .collect(Collectors.groupingBy(SubmissionMoment::userId));

        return enrollmentRepository.findAllByCohortIdWithUser(cohortId).stream()
                .filter(enrollment -> !enrollment.isOperator())
                .map(enrollment -> toRow(enrollment, momentsByUser, assignment.getDueAt()))
                .sorted(Comparator.comparing(row -> row.user().name()))
                .toList();
    }

    /**
     * 과제 삭제 연쇄의 제출 구간 - 디스크 파일을 먼저 지워야 "파일 → 행" 순서가 성립한다 (schema.md 4절).
     * deleteAllInBatch(벌크 쿼리) 금지 - 영속성 컨텍스트를 우회해서, 조회해 둔 Submission이 삭제된
     * Assignment를 참조하는 채로 남아 커밋 시 TransientPropertyValueException이 난다.
     */
    public void deleteAllOf(Long assignmentId) {
        List<Submission> submissions = submissionRepository.findAllByAssignmentId(assignmentId);
        submissions.stream()
                .filter(Submission::hasFile)
                .forEach(submission -> fileStorage.delete(submission.getStoredPath()));
        submissionRepository.deleteAll(submissions);
    }

    // ---- 내부 ----------------------------------------------------------------------------

    private void validate(String codeText, String language, String linkUrl, boolean hasFile, MultipartFile file) {
        if (codeText != null && hasFile) {
            throw new InvalidInputException("본문은 코드와 파일 중 하나만 담을 수 있습니다.");
        }
        if (codeText == null && !hasFile && linkUrl == null) {
            throw new InvalidInputException("코드, 파일, 링크 중 최소 1개는 있어야 합니다.");
        }
        if (language != null && codeText == null) {
            throw new InvalidInputException("제출 언어는 코드 제출에만 지정할 수 있습니다.");
        }
        if (hasFile) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
                throw new InvalidInputException("zip 파일만 업로드할 수 있습니다.");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new InvalidInputException("파일은 20MB 이하여야 합니다.");
            }
        }
    }

    /** 손자 스코프 + 열람 권한을 한 번에 - 부재·체인 불일치·타인 제출물 전부 같은 404 (존재 비노출) */
    private Submission requireViewable(Long cohortId, Long assignmentId, Long submissionId, User viewer) {
        Submission submission = submissionRepository.findByIdAndAssignmentIdWithUser(submissionId, assignmentId)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다."));
        boolean mine = submission.getUser().getId().equals(viewer.getId());
        if (!mine && !viewer.isAdmin() && !isOperator(cohortId, viewer)) {
            throw new NotFoundException("제출물을 찾을 수 없습니다.");
        }
        return submission;
    }

    private boolean isOperator(Long cohortId, User user) {
        return enrollmentRepository.findByCohortIdAndUserId(cohortId, user.getId())
                .map(Enrollment::isOperator)
                .orElse(false);
    }

    /** 제출자의 UserSummary - 직책(title)에 분반 역할이 필요해서 Enrollment을 본다. 소속 해제된 제출자는 role null */
    private UserSummary summaryOf(Long cohortId, User user) {
        EnrollmentRole role = enrollmentRepository.findByCohortIdAndUserId(cohortId, user.getId())
                .map(Enrollment::getRole)
                .orElse(null);
        return UserSummary.of(user, role);
    }

    private StatusBoardRow toRow(Enrollment enrollment, Map<Long, List<SubmissionMoment>> momentsByUser, Instant dueAt) {
        List<SubmissionMoment> moments = momentsByUser.getOrDefault(enrollment.getUser().getId(), List.of());
        List<Instant> submittedAts = moments.stream().map(SubmissionMoment::submittedAt).toList();
        Instant last = submittedAts.stream().max(Comparator.naturalOrder()).orElse(null);
        return new StatusBoardRow(
                UserSummary.of(enrollment.getUser(), enrollment.getRole()),
                SubmissionStatus.from(submittedAts, dueAt),
                moments.size(),
                last);
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }

    /** 분반 부재도 여기서 404가 된다 - 없는 분반의 과제는 findByIdAndCohortId가 비므로 */
    private Assignment requireAssignment(Long cohortId, Long assignmentId) {
        return assignmentRepository.findByIdAndCohortId(assignmentId, cohortId)
                .orElseThrow(() -> new NotFoundException("과제를 찾을 수 없습니다."));
    }

    /** 빈 문자열·공백만 있는 값은 없는 것으로 - "링크만 제출"에서 codeText:"" 같은 FE 폼 잔여값을 걸러낸다 */
    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
