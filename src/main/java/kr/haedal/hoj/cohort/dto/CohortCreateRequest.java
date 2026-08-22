package kr.haedal.hoj.cohort.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 분반 생성 요청. 검증 어노테이션은 요청 DTO 필드에만 단다 (컨트롤러 파라미터에 직접 달지 않는다).
 * 운영진을 생성과 동시에 지정하는 이유: permissions.md 4절 "이름·설명·운영진 지정을 한 번에 처리" (UC-A1).
 */
public record CohortCreateRequest(
        @Schema(description = "분반 이름 (자유 텍스트, 예: 2026-2 C언어)", example = "2026-2 C언어")
        @NotBlank(message = "분반 이름은 비어 있을 수 없습니다.")
        @Size(max = 100, message = "분반 이름은 100자 이하여야 합니다.")
        String name,

        @Schema(description = "설명 (선택)")
        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.")
        String description,

        @Schema(description = "생성과 동시에 지정할 운영진의 loginId 목록 (선택). 아직 로그인한 적 없는 부원도 가능")
        List<@NotBlank(message = "운영진 loginId는 비어 있을 수 없습니다.")
             @Size(max = 50, message = "loginId는 50자 이하여야 합니다.") String> operatorLoginIds
) {
    /** null이면 빈 목록으로 - 컨트롤러/서비스가 null 체크를 반복하지 않게 */
    public List<String> operatorLoginIdsOrEmpty() {
        return operatorLoginIds == null ? List.of() : operatorLoginIds;
    }
}
