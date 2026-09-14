package kr.haedal.ondal.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.attendance.dto.SessionCreateRequest;
import kr.haedal.ondal.attendance.dto.SessionResponse;
import kr.haedal.ondal.attendance.dto.SessionUpdateRequest;
import kr.haedal.ondal.attendance.service.SessionService;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
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
 * 차시 API (#34~#37) - 목록은 분반 소속 누구나, 등록·수정·삭제는 운영진 이상.
 * 메서드마다: 권한 어노테이션 → 검증(@Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 */
@Tag(name = "Session", description = "차시(수업 회차) - 목록은 분반 소속자, 등록·수정·삭제는 운영진 이상. 출석 기록의 단위")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "차시 목록 - 날짜 → 번호 오름차순. attendanceCount = 기록 수")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping
    public List<SessionResponse> list(@PathVariable Long cohortId) {
        return sessionService.findAll(cohortId);
    }

    @Operation(summary = "차시 등록 - sessionNo 생략 시 자동(최대 + 1). 번호 중복 409 CONFLICT, 보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PostMapping
    public ResponseEntity<SessionResponse> create(@PathVariable Long cohortId,
                                                  @RequestBody @Valid SessionCreateRequest request) {
        SessionResponse created = sessionService.create(cohortId, request);
        return ResponseEntity.created(URI.create("/api/cohorts/" + cohortId + "/sessions/" + created.id()))
                .body(created);
    }

    @Operation(summary = "차시 수정 (번호·제목·날짜 전체 교체) - 다른 차시와 번호 중복이면 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PutMapping("/{sessionId}")
    public SessionResponse update(@PathVariable Long cohortId,
                                  @PathVariable Long sessionId,
                                  @RequestBody @Valid SessionUpdateRequest request) {
        return sessionService.update(cohortId, sessionId, request);
    }

    @Operation(summary = "차시 삭제 - 그 차시의 출석 기록도 함께 삭제. 보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long cohortId, @PathVariable Long sessionId) {
        sessionService.delete(cohortId, sessionId);
    }
}
