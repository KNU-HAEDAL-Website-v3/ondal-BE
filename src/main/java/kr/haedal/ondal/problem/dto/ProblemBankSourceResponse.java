package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** GET /api/problems/import/github - 서버가 어느 레포·브랜치를 가져오도록 설정돼 있는지 (관리자 화면의 "깃허브에서 가져오기" 안내) */
public record ProblemBankSourceResponse(
        @Schema(description = "토큰까지 설정돼 있어 가져올 수 있는가 - false 면 파일 업로드만 가능") boolean configured,
        @Schema(description = "레포 (소유자/이름)", example = "KNU-HAEDAL-Website-v3/ondal-problems") String repo,
        @Schema(description = "브랜치 또는 태그", example = "main") String ref
) {
}
