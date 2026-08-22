package kr.haedal.hoj.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.hoj.enrollment.entity.Enrollment;
import kr.haedal.hoj.enrollment.entity.EnrollmentRole;
import kr.haedal.hoj.user.entity.RoleTitle;
import kr.haedal.hoj.user.dto.UserResponse;

import java.time.Instant;

/** 분반 명부의 한 줄. 운영진(OPERATOR) 이상만 볼 수 있다 - 학생에게 타인 정보를 노출하지 않는다. */
public record MemberResponse(
        UserResponse user,
        @Schema(description = "이 분반에서의 역할") EnrollmentRole role,
        @Schema(description = "직책 명칭 - 해구르르 / 교육운영진 / 일반 수강생") RoleTitle title,
        @Schema(description = "소속 등록 시각(UTC)") Instant enrolledAt
) {
    /** user가 fetch join 되어 있거나 트랜잭션 안에서 호출된다는 전제 */
    public static MemberResponse from(Enrollment enrollment) {
        return new MemberResponse(
                UserResponse.from(enrollment.getUser()),
                enrollment.getRole(),
                RoleTitle.of(enrollment.getUser(), enrollment.getRole()),
                enrollment.getCreatedAt()
        );
    }
}
