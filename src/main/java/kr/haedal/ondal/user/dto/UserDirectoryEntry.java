package kr.haedal.ondal.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.cohort.entity.CohortStatus;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.user.entity.GlobalRole;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.entity.UserStatus;

import java.time.Instant;
import java.util.List;

/**
 * 부원 목록(GET /api/users)의 한 줄 - 운영진 이상만 본다 (loginId 노출은 명부 MemberResponse 와 같은 수준).
 * 소속 요약(enrollments)이 붙는 이유: 명부 배정 모달에서 "이미 다른 반 소속" · "이 반에 있음" 배지를 그리려면 필요하고,
 * 부원 관리 화면에서 승인 대기자가 어느 반에도 없음을 한눈에 보이기 위해서다.
 */
public record UserDirectoryEntry(
        Long id,
        String loginId,
        @Schema(description = "표시 이름") String name,
        GlobalRole globalRole,
        @Schema(description = "PENDING = 승인 대기(첫 홈페이지 로그인) / ACTIVE = 이용 가능") UserStatus status,
        @Schema(description = "계정 생성 시각(UTC) - 첫 로그인 또는 선등록 시각") Instant createdAt,
        @Schema(description = "소속 분반 요약 - 보관 분반 포함(cohortStatus 로 구분), 없으면 빈 배열") List<EnrollmentBrief> enrollments
) {
    public record EnrollmentBrief(
            Long cohortId,
            String cohortName,
            CohortStatus cohortStatus,
            @Schema(description = "이 분반에서의 역할") EnrollmentRole role
    ) {
        /** cohort 가 fetch join 되어 있다는 전제 */
        public static EnrollmentBrief from(Enrollment enrollment) {
            return new EnrollmentBrief(
                    enrollment.getCohort().getId(),
                    enrollment.getCohort().getName(),
                    enrollment.getCohort().getStatus(),
                    enrollment.getRole()
            );
        }
    }

    public static UserDirectoryEntry of(User user, List<Enrollment> enrollments) {
        return new UserDirectoryEntry(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                user.getGlobalRole(),
                user.getStatus(),
                user.getCreatedAt(),
                enrollments.stream().map(EnrollmentBrief::from).toList()
        );
    }
}
