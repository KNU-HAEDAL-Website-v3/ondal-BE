package kr.haedal.hoj.cohort.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.hoj.cohort.Cohort;
import kr.haedal.hoj.cohort.CohortStatus;
import kr.haedal.hoj.enrollment.EnrollmentRole;
import kr.haedal.hoj.user.dto.UserResponse;

import java.time.Instant;
import java.util.List;

/**
 * 분반 응답 — 목록·단건·생성·수정 응답이 전부 이 하나의 모양이다.
 * 요청자(viewer)에 따라 달라지는 필드(myRole, canManage, studentCount)가 있으므로
 * 항상 CohortResponseAssembler를 통해 만든다 (엔티티만으로는 만들 수 없다).
 */
public record CohortResponse(
        Long id,
        String name,
        String description,
        CohortStatus status,
        Instant createdAt,

        @Schema(description = "이 분반의 운영진. 학생에게도 공개되는 유일한 타인 정보(질문할 상대를 알아야 하므로)")
        List<UserResponse> operators,

        @Schema(description = "수강생 수. 요청자가 이 분반의 OPERATOR 또는 전역 ADMIN일 때만 값이 있고, STUDENT에게는 null")
        Integer studentCount,

        @Schema(description = "요청자의 이 분반에서의 소속 역할. 비소속(ADMIN이 남의 분반을 볼 때)이면 null")
        EnrollmentRole myRole,

        @Schema(description = "운영 기능(과제 관리·수강생 배정·현황판) 진입 가능 여부. "
                + "분반이 ACTIVE이고 (ADMIN이거나 OPERATOR)일 때 true. 프론트는 이 값만 보고 분기한다")
        boolean canManage
) {
    /** 여러 값을 조합하므로 from(entity)가 아니라 of(...) */
    public static CohortResponse of(Cohort cohort,
                                    List<UserResponse> operators,
                                    Integer studentCount,
                                    EnrollmentRole myRole,
                                    boolean canManage) {
        return new CohortResponse(
                cohort.getId(),
                cohort.getName(),
                cohort.getDescription(),
                cohort.getStatus(),
                cohort.getCreatedAt(),
                operators,
                studentCount,
                myRole,
                canManage
        );
    }
}
