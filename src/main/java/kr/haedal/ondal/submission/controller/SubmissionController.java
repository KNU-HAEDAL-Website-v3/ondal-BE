package kr.haedal.ondal.submission.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.submission.dto.StatusBoardRow;
import kr.haedal.ondal.submission.dto.SubmissionCreateRequest;
import kr.haedal.ondal.submission.dto.SubmissionFile;
import kr.haedal.ondal.submission.dto.SubmissionResponse;
import kr.haedal.ondal.submission.dto.SubmissionSummary;
import kr.haedal.ondal.submission.service.SubmissionService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 제출 API (#18~#22) - 제출·자기 이력은 분반 소속 누구나, 현황판은 운영진 이상.
 * #20·#21의 "본인 또는 운영진" 판정은 서비스 몫 - 어노테이션은 분반 소속까지만 본다.
 * 메서드마다: 권한 어노테이션 → 검증(@Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 */
@Tag(name = "Submission", description = "제출 - 이력 append-only, 상태(미제출/제출/제출(추가)/지각)는 서버가 계산")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/assignments/{assignmentId}")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "제출 - multipart(request JSON 파트 + file zip 파트 선택). 본문(코드/파일 택1)·링크 최소 1개. 마감 후에도 허용(지각 표시). 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponse> create(@PathVariable Long cohortId,
                                                     @PathVariable Long assignmentId,
                                                     @RequestPart("request") @Valid SubmissionCreateRequest request,
                                                     @RequestPart(value = "file", required = false) MultipartFile file,
                                                     @LoginUser User user) {
        SubmissionResponse created = submissionService.create(cohortId, assignmentId, user, request, file);
        return ResponseEntity.created(URI.create(
                        "/api/cohorts/" + cohortId + "/assignments/" + assignmentId + "/submissions/" + created.id()))
                .body(created);
    }

    @Operation(summary = "내 제출 이력 - 최신순, 코드 전문 제외(단건 조회로 확인)")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/submissions/my")
    public List<SubmissionSummary> my(@PathVariable Long cohortId,
                                      @PathVariable Long assignmentId,
                                      @LoginUser User user) {
        return submissionService.findMy(cohortId, assignmentId, user);
    }

    @Operation(summary = "제출 상세 - 본인 또는 운영진 이상. 타인 제출물은 404(존재 비노출)")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/submissions/{submissionId}")
    public SubmissionResponse get(@PathVariable Long cohortId,
                                  @PathVariable Long assignmentId,
                                  @PathVariable Long submissionId,
                                  @LoginUser User user) {
        return submissionService.findOne(cohortId, assignmentId, submissionId, user);
    }

    @Operation(summary = "제출 파일 다운로드 - 권한은 상세와 동일. 파일 없는 제출(코드·링크)은 404")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/submissions/{submissionId}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long cohortId,
                                                 @PathVariable Long assignmentId,
                                                 @PathVariable Long submissionId,
                                                 @LoginUser User user) {
        SubmissionFile file = submissionService.loadFile(cohortId, assignmentId, submissionId, user);
        // "ResponseEntity 금지" 규약의 명시적 예외 - 바이너리 응답은 Content-Disposition(한글 파일명 RFC 5987 인코딩)이 필요하다
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.resource());
    }

    @Operation(summary = "[운영진] 현황판 - 현재 수강생 명단(이름순) x 상태/건수/최근 제출. 미제출자 포함")
    @CohortRole(EnrollmentRole.OPERATOR)
    @GetMapping("/status-board")
    public List<StatusBoardRow> statusBoard(@PathVariable Long cohortId,
                                            @PathVariable Long assignmentId) {
        return submissionService.statusBoard(cohortId, assignmentId);
    }
}
