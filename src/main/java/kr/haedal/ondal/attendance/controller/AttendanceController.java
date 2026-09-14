package kr.haedal.ondal.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.attendance.dto.AttendanceMarkRequest;
import kr.haedal.ondal.attendance.dto.AttendanceRosterResponse;
import kr.haedal.ondal.attendance.dto.MyAttendanceResponse;
import kr.haedal.ondal.attendance.service.AttendanceService;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.user.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출석 API (#38~#40) - 명부·표시는 운영진 이상, 내 출석은 분반 소속 누구나.
 * 경로 prefix 가 둘(sessions/{sessionId}/attendances, attendances/me)이라 클래스 레벨 @RequestMapping 없이 메서드에 전체 경로 (EnrollmentController 선례).
 */
@Tag(name = "Attendance", description = "출석 - 차시 명부·표시는 운영진 이상, 내 출석은 분반 소속자. 미확인 = 기록 없음(status null)")
@RestController
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "차시 출석 명부 (운영진 이상) - 행 = 현재 수강생(이름순), 요약·학생별 누계 포함")
    @CohortRole(EnrollmentRole.OPERATOR)
    @GetMapping("/api/cohorts/{cohortId}/sessions/{sessionId}/attendances")
    public AttendanceRosterResponse roster(@PathVariable Long cohortId, @PathVariable Long sessionId) {
        return attendanceService.roster(cohortId, sessionId);
    }

    @Operation(summary = "출석 표시 (운영진 이상, 일괄 upsert) - status null 은 기록 삭제. 갱신된 명부를 돌려준다. 수강생이 아니면 404, 보관 분반 409")
    @CohortRole(EnrollmentRole.OPERATOR)
    @PutMapping("/api/cohorts/{cohortId}/sessions/{sessionId}/attendances")
    public AttendanceRosterResponse mark(@PathVariable Long cohortId,
                                         @PathVariable Long sessionId,
                                         @RequestBody @Valid AttendanceMarkRequest request,
                                         @LoginUser User me) {
        return attendanceService.mark(cohortId, sessionId, request, me);
    }

    @Operation(summary = "내 출석 (분반 소속자) - 누계(출석률)와 차시별 기록, 최신 차시 먼저")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/api/cohorts/{cohortId}/attendances/me")
    public MyAttendanceResponse my(@PathVariable Long cohortId, @LoginUser User me) {
        return attendanceService.my(cohortId, me);
    }
}
