package kr.haedal.hoj.user.dto;

import kr.haedal.hoj.user.GlobalRole;
import kr.haedal.hoj.user.User;

/**
 * 엔티티를 API에 직접 노출하지 않고 응답 전용 DTO로 감싼다.
 * 엔티티 필드가 늘어나도 API 계약이 저절로 바뀌지 않게 하는 격벽.
 * "사용자 요약"이 필요한 모든 응답(로그인 정보, 분반 운영진 목록, 명부 등)은 이 하나의 모양만 쓴다.
 */
public record UserResponse(
        Long id,
        String loginId,
        String name,
        GlobalRole globalRole
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLoginId(), user.getName(), user.getGlobalRole());
    }
}
