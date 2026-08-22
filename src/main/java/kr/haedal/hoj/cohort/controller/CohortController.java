package kr.haedal.hoj.cohort.controller;

import kr.haedal.hoj.cohort.entity.CohortStatus;
import kr.haedal.hoj.cohort.service.CohortService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.hoj.auth.LoginUser;
import kr.haedal.hoj.auth.authorization.AdminOnly;
import kr.haedal.hoj.auth.authorization.CohortRole;
import kr.haedal.hoj.cohort.dto.CohortCreateRequest;
import kr.haedal.hoj.cohort.dto.CohortResponse;
import kr.haedal.hoj.cohort.dto.CohortUpdateRequest;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 분반 API - 이후 모든 도메인 컨트롤러의 기준 패턴.
 * 메서드마다: 권한 어노테이션 → (검증 @Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 * 이 컨트롤러는 CohortService만 주입한다. 소속(운영진·명부·내 분반)은 EnrollmentController.
 */
@Tag(name = "Cohort", description = "분반 - 생성·수정·보관은 관리자(ADMIN), 조회는 소속자")
@RestController
@RequestMapping("/api/cohorts")
public class CohortController {

    private final CohortService cohortService;

    public CohortController(CohortService cohortService) {
        this.cohortService = cohortService;
    }

    @Operation(summary = "[관리자] 분반 목록 - 기본은 진행 중(ACTIVE), 보관함은 ?status=ARCHIVED")
    @AdminOnly
    @GetMapping
    public List<CohortResponse> list(@RequestParam(defaultValue = "ACTIVE") CohortStatus status,
                                     @LoginUser User me) {
        return cohortService.findAll(status, me);
    }

    @Operation(summary = "[관리자] 분반 생성 (운영진 동시 지정 가능)")
    @AdminOnly
    @PostMapping
    public ResponseEntity<CohortResponse> create(@RequestBody @Valid CohortCreateRequest request,
                                                 @LoginUser User me) {
        CohortResponse created = cohortService.create(request, me);
        // 계약은 본문의 id. Location은 REST 관례상 덧붙이는 것 (CORS exposedHeaders 없이는 브라우저에서 못 읽음)
        return ResponseEntity.created(URI.create("/api/cohorts/" + created.id())).body(created);
    }

    @Operation(summary = "분반 상세 - 소속자(운영진·수강생) 또는 관리자")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/{cohortId}")
    public CohortResponse get(@PathVariable Long cohortId, @LoginUser User me) {
        return cohortService.findOne(cohortId, me);
    }

    @Operation(summary = "[관리자] 분반 수정 (이름·설명 전체 교체). 보관 중이면 409")
    @AdminOnly
    @PutMapping("/{cohortId}")
    public CohortResponse update(@PathVariable Long cohortId,
                                 @RequestBody @Valid CohortUpdateRequest request,
                                 @LoginUser User me) {
        return cohortService.update(cohortId, request, me);
    }

    @Operation(summary = "[관리자] 분반 보관 (멱등). 보관되면 누구도 변경 불가, 열람만")
    @AdminOnly
    @PostMapping("/{cohortId}/archive")
    public CohortResponse archive(@PathVariable Long cohortId, @LoginUser User me) {
        return cohortService.archive(cohortId, me);
    }

    @Operation(summary = "[관리자] 분반 보관 해제 (멱등)")
    @AdminOnly
    @PostMapping("/{cohortId}/restore")
    public CohortResponse restore(@PathVariable Long cohortId, @LoginUser User me) {
        return cohortService.restore(cohortId, me);
    }
}
