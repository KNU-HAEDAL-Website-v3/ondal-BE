package kr.haedal.ondal.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.authorization.OperatorAnywhere;
import kr.haedal.ondal.user.dto.UserDirectoryEntry;
import kr.haedal.ondal.user.entity.UserStatus;
import kr.haedal.ondal.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 부원 목록·승인 (docs 결정 10). 두 API 모두 **운영진 이상**(@OperatorAnywhere - 전역 ADMIN 이거나 어느 분반이든 교육운영진).
 *
 * 승인 권한을 운영진까지 넓힌 이유(PM, 2026-09-19 "안 B"): 부트캠프 첫날 수십 명이 한꺼번에 로그인하는데 해구르르 혼자 처리하면 병목.
 * 분반 운영진이 자기 반 명부를 짜면서 처리하는 편이 자연스럽다. 승인 = "부원임을 확인" 수준이라 분반 배정과 같은 신뢰 수준이다.
 */
@Tag(name = "User", description = "부원 목록·승인 (운영진 이상) - 첫 홈페이지 로그인 계정은 승인 대기(PENDING), 승인하거나 분반에 배정하면 ACTIVE")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "부원 목록 (운영진 이상) - 소속 분반 요약 포함. 승인 대기가 먼저, 그 다음 최근 생성순. status 로 거르기(PENDING/ACTIVE)")
    @OperatorAnywhere
    @GetMapping
    public List<UserDirectoryEntry> list(@RequestParam(required = false) UserStatus status) {
        return userService.directory(status);
    }

    @Operation(summary = "승인 (운영진 이상, 멱등) - 대기 중이면 ACTIVE 로. 이미 ACTIVE 면 그대로 200. 없는 사용자는 404")
    @OperatorAnywhere
    @PostMapping("/{userId}/approve")
    public UserDirectoryEntry approve(@PathVariable Long userId) {
        return userService.approve(userId);
    }
}
