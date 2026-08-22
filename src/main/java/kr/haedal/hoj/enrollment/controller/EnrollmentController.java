package kr.haedal.hoj.enrollment.controller;

import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.enrollment.service.EnrollmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.hoj.auth.LoginUser;
import kr.haedal.hoj.auth.authorization.AdminOnly;
import kr.haedal.hoj.auth.authorization.CohortRole;
import kr.haedal.hoj.auth.authorization.LoginOnly;
import kr.haedal.hoj.cohort.dto.CohortResponse;
import kr.haedal.hoj.enrollment.dto.MemberResponse;
import kr.haedal.hoj.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 소속 API - 내 분반, 분반 명부, 운영진 지정/해제.
 * 경로 prefix가 제각각(/api/me/..., /api/cohorts/{cohortId}/...)이라 클래스 레벨 @RequestMapping 없이 메서드에 전체 경로를 쓴다.
 * 다음 슬라이스: POST /api/cohorts/{cohortId}/students, DELETE .../students/{loginId} 를 이 컨트롤러에 추가.
 */
@Tag(name = "Enrollment", description = "소속 - 내 분반 목록, 명부(운영진 이상), 운영진 지정/해제(관리자)")
@RestController
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @Operation(summary = "내가 소속된 분반 목록 - 보관 분반 포함(status로 구분), ACTIVE 먼저. 빈 배열 = 미소속")
    @LoginOnly
    @GetMapping("/api/me/cohorts")
    public List<CohortResponse> myCohorts(@LoginUser User me) {
        return enrollmentService.findMyCohorts(me);
    }

    @Operation(summary = "분반 명부 (운영진 이상만). 학생은 403")
    @CohortRole(EnrollmentRole.OPERATOR)
    @GetMapping("/api/cohorts/{cohortId}/members")
    public List<MemberResponse> members(@PathVariable Long cohortId) {
        return enrollmentService.findMembers(cohortId);
    }

    @Operation(summary = "[관리자] 운영진 지정 (멱등) - 미소속이면 소속시키고, 수강생이면 승격. 아직 로그인한 적 없는 loginId도 가능")
    @AdminOnly
    @PutMapping("/api/cohorts/{cohortId}/operators/{loginId}")
    public MemberResponse assignOperator(@PathVariable Long cohortId, @PathVariable String loginId) {
        return enrollmentService.promoteToOperator(cohortId, loginId);
    }

    @Operation(summary = "[관리자] 운영진 해제 - 운영진(OPERATOR) 소속만 지운다. 수강생이거나 미소속이면 404")
    @AdminOnly
    @DeleteMapping("/api/cohorts/{cohortId}/operators/{loginId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOperator(@PathVariable Long cohortId, @PathVariable String loginId) {
        enrollmentService.remove(cohortId, loginId, EnrollmentRole.OPERATOR);
    }
}
