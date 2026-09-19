package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** POST /api/problems/import/github 결과 - 어느 커밋을 가져왔는지와 가져오기 집계 */
public record ProblemBankSyncResult(
        @Schema(description = "레포 (소유자/이름)") String repo,
        @Schema(description = "가져온 브랜치·태그") String ref,
        @Schema(description = "그 시점 ref 의 커밋 SHA (전체)") String commitSha,
        @Schema(description = "레포에서 읽은 문제 수") int problemsInRepo,
        @Schema(description = "가져온 시각(UTC)") Instant importedAt,
        @Schema(description = "가져오기 집계 - 파일 업로드와 같은 규칙") ProblemImportResult result
) {
}
