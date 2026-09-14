package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** #49 출제 도구 실행 - 저장 없이 정답 코드를 입력들에 대해 실행. expectedOutputs 가 있으면 판정까지 (출제 검증), 없으면 출력만 (기대 출력 채우기) */
public record JudgeRunRequest(
        @Schema(description = "언어 - FE 셀렉트 문자열 그대로", example = "C")
        @NotBlank(message = "언어는 비어 있을 수 없습니다.")
        String language,

        @Schema(description = "정답 코드")
        @NotBlank(message = "코드는 비어 있을 수 없습니다.")
        @Size(max = 100000, message = "코드는 100000자 이하여야 합니다.")
        String sourceCode,

        @Schema(description = "입력들 - 표의 입력 열. 1~20개")
        @NotNull(message = "입력 목록은 null 일 수 없습니다.")
        @Size(min = 1, max = 20, message = "입력은 1~20개여야 합니다.")
        List<String> inputs,

        @Schema(description = "기대 출력들(선택) - 주면 inputs 와 개수가 같아야 하고 케이스별 verdict 를 채운다")
        List<String> expectedOutputs,

        @Schema(description = "시간 제한(ms) - 폼의 현재 값. null 이면 기본값") Integer timeLimitMs,
        @Schema(description = "메모리 제한(MB) - 폼의 현재 값. null 이면 기본값") Integer memoryLimitMb
) {
}
