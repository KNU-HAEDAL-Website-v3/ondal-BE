package kr.haedal.hoj.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.hoj.user.User;

/**
 * 타인에게 보여줘도 되는 최소 사용자 정보 — id(목록 키 용도)와 이름뿐.
 * 학생 화면에 내려가는 운영진 목록이 이걸 쓴다. loginId·globalRole 은 본인(UserResponse)과 운영진 이상이 보는 명부(MemberResponse)에만.
 * "학생은 다른 사람의 정보를 볼 수 없다"(docs: cohort/design.md §4) 원칙의 DTO 표현.
 */
public record UserSummary(
        Long id,
        @Schema(description = "표시 이름") String name
) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName());
    }
}
