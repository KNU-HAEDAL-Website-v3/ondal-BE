package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 정답 코드 1건 - PUT .../solutions 의 원소이자 번들 가져오기(ImportProblem.solutions)의 원소. 같은 모양이라 하나로 */
public record ProblemSolutionPayload(
        @Schema(description = "언어 - 제출 언어와 같은 표기 (C, C++, Java, Python 3, JavaScript, TypeScript). 지원하지 않는 이름·중복은 400", example = "Python 3")
        @NotBlank(message = "언어는 비어 있을 수 없습니다.")
        @Size(max = 30, message = "언어 이름은 30자 이하여야 합니다.")
        String language,

        @Schema(description = "정답 코드 전문")
        @NotBlank(message = "정답 코드는 비어 있을 수 없습니다.")
        @Size(max = 100000, message = "정답 코드는 100000자 이하여야 합니다.")
        String codeText
) {
}
