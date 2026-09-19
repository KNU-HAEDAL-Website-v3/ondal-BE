package kr.haedal.ondal.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.user.dto.MyStatsResponse;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.service.MyStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 - 본인 활동 요약. 내 정보는 /api/auth/me, 내 분반은 /api/me/cohorts(EnrollmentController) 가 이미 있으므로 여기는 숫자만.
 * 2026-09-19: 원안(피그마·와이어프레임)에 마이페이지가 없어 신설 - 이름·아이디·소속·활동·에디터 테마 설정을 한 화면에.
 */
@Tag(name = "Me", description = "마이페이지 - 내 활동 요약 (로그인 누구나, 본인 것만)")
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MyStatsService myStatsService;

    public MeController(MyStatsService myStatsService) {
        this.myStatsService = myStatsService;
    }

    @Operation(summary = "내 활동 요약 - 가입 시각, 과제 제출 수, 연습 제출 수, 맞힌 문제 수")
    @LoginOnly
    @GetMapping("/stats")
    public MyStatsResponse stats(@LoginUser User me) {
        return myStatsService.statsOf(me);
    }
}
