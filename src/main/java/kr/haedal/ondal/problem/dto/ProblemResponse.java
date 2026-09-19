package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** 문제 단건 - 목록 행에 본문·제한·권한 판정값을 더한 모양 */
public record ProblemResponse(
        Long id,
        Integer problemNo,
        String title,

        @Schema(description = "문제 본문 - 자유 텍스트 (선택)")
        String description,

        List<TagResponse> tags,

        @Schema(description = "실행 시간 제한(ms) - 서버 기본값이 적용된 실제 값")
        int timeLimitMs,

        @Schema(description = "메모리 제한(MB) - 서버 기본값이 적용된 실제 값")
        int memoryLimitMb,

        boolean judgeEnabled,
        int assignedCount,
        boolean solved,

        @Schema(description = "출제자 이름 - V7 이전에 만들어진 문제는 알 수 없어 null")
        String createdBy,

        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "요청자가 이 문제를 고칠 수 있는가 - 프론트는 이 값만 보고 수정·삭제 버튼을 분기한다")
        boolean canEdit,

        @Schema(description = "난이도 1~25 - 표기 \"대분류-소분류\" 는 FE 몫. null = 미지정")
        Integer difficulty,

        @Schema(description = "제출 허용 언어 - 빈 배열이면 제한 없음. 제출 폼의 언어 선택지를 이 목록으로 좁힌다")
        List<String> allowedLanguages
) {
}
