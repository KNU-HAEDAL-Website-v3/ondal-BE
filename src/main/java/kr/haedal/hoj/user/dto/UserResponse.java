package kr.haedal.hoj.user.dto;

import kr.haedal.hoj.user.entity.GlobalRole;
import kr.haedal.hoj.user.entity.User;

/**
 * 엔티티를 API에 직접 노출하지 않고 응답 전용 DTO로 감싼다.
 * 엔티티 필드가 늘어나도 API 계약이 저절로 바뀌지 않게 하는 격벽.
 * 본인 정보(/api/auth/me)와 운영진 이상이 보는 명부(MemberResponse)에 쓴다.
 * 학생에게 내려가는 타인 정보(분반 카드의 운영진 목록)는 이름만 담은 UserSummary 를 쓴다 - loginId·globalRole 비노출.
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
