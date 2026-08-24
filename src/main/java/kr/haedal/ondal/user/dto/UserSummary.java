package kr.haedal.ondal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.user.entity.RoleTitle;
import kr.haedal.ondal.user.entity.User;

/**
 * 타인에게 보여줘도 되는 최소 사용자 정보 - id(목록 키 용도), 이름, 직책 명칭.
 * 학생 화면에 내려가는 운영진 목록이 이걸 쓴다. loginId·globalRole 은 본인(UserResponse)과 운영진 이상이 보는 명부(MemberResponse)에만.
 * "학생은 다른 사람의 정보를 볼 수 없다"(docs: cohort/design.md 4절) 원칙의 DTO 표현.
 */
public record UserSummary(
        Long id,
        @Schema(description = "표시 이름") String name,
        @Schema(description = "직책 명칭 - 해구르르(임원) / 교육운영진 / 일반 수강생. 서버가 정한 문자열을 그대로 표시한다 (RoleTitle)")
        RoleTitle title
) {
    /** roleInCohort: 이 사람의 해당 분반 역할. 임원(ADMIN)은 역할과 무관하게 해구르르 */
    public static UserSummary of(User user, EnrollmentRole roleInCohort) {
        return new UserSummary(user.getId(), user.getName(), RoleTitle.of(user, roleInCohort));
    }
}
