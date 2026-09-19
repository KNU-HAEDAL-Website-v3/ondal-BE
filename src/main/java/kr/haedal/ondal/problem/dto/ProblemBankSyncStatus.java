package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.common.error.ErrorResponse;

import java.time.Instant;

/**
 * 깃허브 가져오기 작업 상태 - POST 는 작업을 시작하고 이 상태를 돌려주며(202), 화면은 GET .../status 로 끝날 때까지 폴링한다.
 * 가져오기는 수 초~수십 초 걸려 프록시(nginx 60초·Cloudflare 100초)를 넘길 수 있어 요청 하나로 기다리지 않는다.
 */
public record ProblemBankSyncStatus(
        @Schema(description = "IDLE(한 번도 안 함) / RUNNING / DONE / FAILED") State state,
        @Schema(description = "진행 단계 - 커밋 조회 · zip 다운로드 · 레포 읽기 · 문제 넣는 중") String step,
        @Schema(description = "넣은 문제 수 (건너뛴 것 포함)") int processed,
        @Schema(description = "레포에서 읽은 문제 수 - 읽기 전에는 0") int total,
        Instant startedAt,
        Instant finishedAt,
        @Schema(description = "GitHub 에서 받는 데 걸린 시간(ms)") Long fetchMs,
        @Schema(description = "DB 에 넣는 데 걸린 시간(ms)") Long importMs,
        boolean overwrite,
        @Schema(description = "시작한 관리자 이름") String requestedBy,
        @Schema(description = "DONE 일 때 결과") ProblemBankSyncResult outcome,
        @Schema(description = "FAILED 일 때 원인 (code, message)") ErrorResponse error
) {
    public enum State { IDLE, RUNNING, DONE, FAILED }
}
