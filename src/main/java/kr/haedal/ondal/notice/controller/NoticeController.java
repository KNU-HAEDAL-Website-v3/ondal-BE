package kr.haedal.ondal.notice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.AdminOnly;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.notice.dto.NoticeCreateRequest;
import kr.haedal.ondal.notice.dto.NoticeResponse;
import kr.haedal.ondal.notice.dto.NoticeUpdateRequest;
import kr.haedal.ondal.notice.service.NoticeService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 공지사항 API (#28~#33). 공지 id 는 전역이라 대부분 /api/notices 아래에, 분반 공지 등록만 /api/cohorts/{cohortId}/notices 에 둔다 -
 * 어노테이션(@CohortRole)이 분반 소속을 판정하려면 경로에 cohortId 가 필요하기 때문 (EnrollmentController 처럼 메서드에 전체 경로).
 * 수정·삭제는 @LoginOnly + 서비스 판정(전체: 관리자 / 분반: 운영진 이상) - 경로에 분반이 없어 어노테이션으로 표현할 수 없는 경우.
 * 메서드마다: 권한 어노테이션 → 검증(@Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 */
@Tag(name = "Notice", description = "공지사항 - 전체 공지(관리자)와 분반 공지(운영진 이상). 조회는 로그인 사용자(전체 공지 + 소속 분반 공지)")
@RestController
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "공지 목록 - 전체 공지 + 내 소속 분반 공지(관리자는 전부). 필독 먼저 → 최신순. canEdit·canDelete 는 요청자 의존")
    @LoginOnly
    @GetMapping("/api/notices")
    public List<NoticeResponse> list(@LoginUser User me) {
        return noticeService.findAllVisible(me);
    }

    @Operation(summary = "공지 상세 - 분반 공지는 소속자·관리자만, 비소속이면 403")
    @LoginOnly
    @GetMapping("/api/notices/{noticeId}")
    public NoticeResponse get(@PathVariable Long noticeId, @LoginUser User me) {
        return noticeService.findOne(noticeId, me);
    }

    @Operation(summary = "[관리자] 전체 공지 등록 - 로그인한 누구나 보는 공지")
    @AdminOnly
    @PostMapping("/api/notices")
    public ResponseEntity<NoticeResponse> createGlobal(@RequestBody @Valid NoticeCreateRequest request,
                                                       @LoginUser User me) {
        NoticeResponse created = noticeService.createGlobal(request, me);
        return ResponseEntity.created(URI.create("/api/notices/" + created.id())).body(created);
    }

    @Operation(summary = "분반 공지 등록 - 그 분반 운영진 이상(관리자 포함). 보관 분반이면 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PostMapping("/api/cohorts/{cohortId}/notices")
    public ResponseEntity<NoticeResponse> createForCohort(@PathVariable Long cohortId,
                                                          @RequestBody @Valid NoticeCreateRequest request,
                                                          @LoginUser User me) {
        NoticeResponse created = noticeService.createForCohort(cohortId, request, me);
        // 공지 id 는 전역 - 상세·수정·삭제는 /api/notices/{id}
        return ResponseEntity.created(URI.create("/api/notices/" + created.id())).body(created);
    }

    @Operation(summary = "공지 수정 (전체 교체) - 전체 공지는 관리자, 분반 공지는 그 분반 운영진 이상. 아니면 403, 보관 분반이면 409")
    @LoginOnly
    @PutMapping("/api/notices/{noticeId}")
    public NoticeResponse update(@PathVariable Long noticeId,
                                 @RequestBody @Valid NoticeUpdateRequest request,
                                 @LoginUser User me) {
        return noticeService.update(noticeId, request, me);
    }

    @Operation(summary = "공지 삭제 - 수정과 같은 권한. 아니면 403, 보관 분반이면 409")
    @LoginOnly
    @DeleteMapping("/api/notices/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long noticeId, @LoginUser User me) {
        noticeService.delete(noticeId, me);
    }
}
