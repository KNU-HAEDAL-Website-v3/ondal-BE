package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT /api/problems/{id}/solutions - 정답 코드 통째 교체 (운영진 이상). 빈 배열 = 모두 삭제 */
public record ProblemSolutionsRequest(
        @Schema(description = "언어별 정답 코드 전체 - 준 목록이 곧 결과. 최대 6개(지원 언어 수), 같은 언어가 둘이면 400")
        @NotNull(message = "solutions 는 null 일 수 없습니다. 모두 지우려면 빈 배열로 보내세요.")
        @Size(max = 6, message = "정답 코드는 언어별 1개, 최대 6개까지 저장할 수 있습니다.")
        @Valid
        List<ProblemSolutionPayload> solutions
) {
}
