package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 테스트케이스 1행 (PUT 통째 교체의 원소). 빈 문자열 허용 - 입력이 없는 문제도 있다 */
public record TestCaseRequest(
        @Schema(description = "표준 입력 - 그대로 프로그램에 들어간다 (줄바꿈 포함)", example = "1 2\n")
        @NotNull(message = "입력은 null 일 수 없습니다. 입력이 없으면 빈 문자열로 보내세요.")
        @Size(max = 65536, message = "입력은 64KB 이하여야 합니다.")
        String input,

        @Schema(description = "기대 출력 - 비교는 줄 끝 공백·마지막 빈 줄 무시", example = "3\n")
        @NotNull(message = "기대 출력은 null 일 수 없습니다.")
        @Size(max = 65536, message = "기대 출력은 64KB 이하여야 합니다.")
        String expectedOutput,

        @Schema(description = "공개 케이스 - 학생에게 예시로 보이고 채점 결과에서 실제 출력까지 노출. 생략 시 false")
        Boolean isPublic
) {
    public boolean publicFlag() {
        return Boolean.TRUE.equals(isPublic);
    }
}
