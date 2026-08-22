package kr.haedal.hoj.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 수강생 일괄 배정 요청. 명단을 통째로 붙여넣는 UC-O2 용도라 목록으로 받는다.
 * 빈 목록은 실수로 본다(400) — "아무도 배정 안 함"은 요청할 이유가 없다.
 */
public record StudentAssignRequest(
        @Schema(description = "배정할 수강생의 loginId 목록. 아직 로그인한 적 없는 부원도 가능(선등록). 중복은 한 번만 처리")
        @NotEmpty(message = "loginIds는 비어 있을 수 없습니다.")
        List<@NotBlank(message = "loginId는 비어 있을 수 없습니다.")
             @Size(max = 50, message = "loginId는 50자 이하여야 합니다.") String> loginIds
) {
}
