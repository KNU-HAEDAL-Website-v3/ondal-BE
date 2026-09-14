package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** #48 채점 설정·테스트케이스 저장 - 통째 교체 (docs judge/design.md 결정 9). 케이스 0개 = 자동 채점 해제 */
public record JudgeConfigRequest(
        @Schema(description = "시간 제한(ms) - null 이면 서버 기본값(2000). 범위 100 ~ 서버 상한(15000)", example = "2000")
        Integer timeLimitMs,

        @Schema(description = "메모리 제한(MB) - null 이면 서버 기본값(256). 범위 16 ~ 서버 상한(512)", example = "256")
        Integer memoryLimitMb,

        @Schema(description = "테스트케이스 전체 - 저장 순서 = 실행 순서. 최대 50개")
        @NotNull(message = "테스트케이스 목록은 null 일 수 없습니다. 없으면 빈 배열로 보내세요.")
        @Valid
        List<TestCaseRequest> testCases,

        @Schema(description = "true 면 이 과제의 기존 코드 제출을 전부 다시 채점한다 (응답 rejudgeQueued 에 건수)")
        boolean rejudge
) {
}
