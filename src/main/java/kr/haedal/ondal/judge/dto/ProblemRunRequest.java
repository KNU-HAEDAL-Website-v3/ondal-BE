package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * "내 입력으로 실행" (docs hoj/api.md 8절) - 학생이 자기 코드를 직접 넣은 입력으로 돌려본다. 저장·판정 없음, 출력만.
 * 출제 도구(JudgeRunRequest)보다 좁다: 입력 1~5개·각 10,000자, 기대 출력·제한 지정 없음 - Judge0 부하를 학생 수만큼 곱하지 않기 위해
 */
public record ProblemRunRequest(
        @Schema(description = "언어 - 제출 폼 셀렉트 문자열 그대로. 문제의 허용 언어 밖이면 400", example = "Python 3")
        @NotBlank(message = "언어는 비어 있을 수 없습니다.")
        String language,

        @Schema(description = "실행할 코드")
        @NotBlank(message = "코드는 비어 있을 수 없습니다.")
        @Size(max = 100000, message = "코드는 100000자 이하여야 합니다.")
        String sourceCode,

        @Schema(description = "표준 입력들 - 1~5개, 각 10000자 이하. 입력마다 한 번씩 실행한다")
        @NotNull(message = "입력 목록은 null 일 수 없습니다.")
        @Size(min = 1, max = 5, message = "입력은 1~5개여야 합니다.")
        List<@NotNull(message = "입력은 null 일 수 없습니다. 입력이 없으면 빈 문자열로 보내세요.")
             @Size(max = 10000, message = "입력은 10000자 이하여야 합니다.") String> inputs
) {
}
