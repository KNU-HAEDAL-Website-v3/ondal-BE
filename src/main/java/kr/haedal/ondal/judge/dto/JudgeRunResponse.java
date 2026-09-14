package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.entity.Verdict;

import java.util.List;

/** #49 응답 - 컴파일 에러면 compileOutput 만, 아니면 입력 순서대로 runs */
public record JudgeRunResponse(
        @Schema(description = "컴파일 에러 메시지 - 성공이면 null") String compileOutput,
        List<Run> runs
) {
    public record Run(
            int index,
            String stdout,
            String stderr,
            @Schema(description = "expectedOutputs 를 준 경우의 판정 - 아니면 null. 실행 실패(TLE 등)는 기대 출력이 없어도 값") Verdict verdict,
            Integer timeMs,
            Integer memoryKb
    ) {
    }
}
