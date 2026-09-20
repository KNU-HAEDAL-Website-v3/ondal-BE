package kr.haedal.ondal.hoj.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.hoj.dto.HojUserPageResponse;
import kr.haedal.ondal.hoj.service.HojUserPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 페이지 API (docs hoj/api.md 3절) - 로그인한 누구나 누구의 페이지든. 이름·활동만, loginId 없음 */
@Tag(name = "HOJ")
@RestController
@RequestMapping("/api/hoj/users")
public class HojUserController {

    private final HojUserPageService hojUserPageService;

    public HojUserController(HojUserPageService hojUserPageService) {
        this.hojUserPageService = hojUserPageService;
    }

    @Operation(summary = "사용자 페이지 - 통계·언어 비율·푼 문제·시도 중·태그 숙련도·활동 잔디(365일, KST)·최근 제출 20건·순위. 없는 사용자 404")
    @LoginOnly
    @GetMapping("/{userId}")
    public HojUserPageResponse page(@PathVariable Long userId) {
        return hojUserPageService.pageOf(userId);
    }
}
