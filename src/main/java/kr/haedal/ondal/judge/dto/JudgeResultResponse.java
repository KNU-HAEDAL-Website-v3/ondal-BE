package kr.haedal.ondal.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.entity.JudgeStatus;
import kr.haedal.ondal.judge.entity.Verdict;

import java.time.Instant;
import java.util.List;

/**
 * 제출 응답(#18·#20)의 judge 필드 - 채점 대상(CODE + 자동 채점 문제)일 때만 값, 아니면 null.
 * 공개 케이스만 입력·기대 출력·실제 출력이 실린다 - 운영진에게도 같은 규칙(비공개 입력은 #47).
 */
public record JudgeResultResponse(
        @Schema(description = "PENDING(대기) / RUNNING(실행 중) / DONE(판정 있음) / ERROR(엔진 장애 - verdict JUDGE_ERROR)") JudgeStatus status,
        @Schema(description = "판정 - DONE·ERROR 일 때만. ACCEPTED / WRONG_ANSWER / TIME_LIMIT / MEMORY_LIMIT / RUNTIME_ERROR / COMPILE_ERROR / JUDGE_ERROR") Verdict verdict,
        int passedCases,
        int totalCases,
        Integer maxTimeMs,
        Integer maxMemoryKb,
        @Schema(description = "컴파일 에러 메시지(8KB) 또는 채점 오류 사유") String compileOutput,
        List<Case> cases,
        Instant judgedAt
) {
    public record Case(
            int position,
            Verdict verdict,
            Integer timeMs,
            Integer memoryKb,
            boolean isPublic,
            @Schema(description = "공개 케이스만") String input,
            @Schema(description = "공개 케이스만") String expectedOutput,
            @Schema(description = "공개 케이스만 - 4KB 로 잘림(truncated)") String actualOutput,
            boolean truncated
    ) {
    }
}
