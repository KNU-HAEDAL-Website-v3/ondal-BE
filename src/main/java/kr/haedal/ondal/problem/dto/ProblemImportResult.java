package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 문제 번들 가져오기 결과 - 관리자 화면이 "N개 추가 · M개 갱신 · K개 건너뜀" 으로 보인다 */
public record ProblemImportResult(
        @Schema(description = "새로 만든 문제 수") int created,
        @Schema(description = "같은 번호가 있어 덮어쓴 문제 수 (overwrite=true 일 때만)") int updated,
        @Schema(description = "같은 번호가 있어 건너뛴 문제 수 (overwrite=false 일 때)") int skipped,
        @Schema(description = "이번에 새로 만든 태그 이름") List<String> createdTags,
        @Schema(description = "처리한 문제 번호 (건너뛴 것 제외)") List<Integer> problemNos
) {
}
