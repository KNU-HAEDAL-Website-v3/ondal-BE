package kr.haedal.hoj.auth.dto;

import kr.haedal.hoj.user.GlobalRole;
import kr.haedal.hoj.user.User;

/**
 * 엔티티를 API에 직접 노출하지 않고 응답 전용 DTO로 감싼다.
 * 엔티티 필드가 늘어나도 API 계약이 저절로 바뀌지 않게 하는 격벽.
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
