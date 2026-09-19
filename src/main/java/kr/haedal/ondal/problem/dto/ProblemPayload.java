package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 문제 등록·수정 요청 (운영진 이상) - 등록과 수정이 같은 모양(PUT 전체 교체).
 * 테스트케이스·실행 제한은 채점 설정 API(.../judge)에서 따로 저장한다 - 출제 화면은 두 번 부른다.
 */
public record ProblemPayload(
        @Schema(description = "문제 번호 (선택) - 비우면 자동 채번(현재 최대+1, 1000 시작). 수정에서 비우면 기존 번호 유지. 중복이면 409", example = "1003")
        @Min(value = 1000, message = "문제 번호는 1000 이상이어야 합니다.")
        Integer problemNo,

        @Schema(description = "문제 제목", example = "두 수의 합")
        @NotBlank(message = "문제 제목은 비어 있을 수 없습니다.")
        @Size(max = 200, message = "문제 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "문제 본문 (선택, 마크다운)")
        @Size(max = 10000, message = "문제 본문은 10000자 이하여야 합니다.")
        String description,

        @Schema(description = "붙일 태그 id 목록 - 통째 교체(빈 목록이면 태그 없음). 없는 id 가 섞이면 400")
        List<Long> tagIds,

        @Schema(description = "난이도 1~25 ((대분류-1)*5+소분류, 표기 1-1 ~ 5-5). null = 미지정", example = "7")
        @Min(value = 1, message = "난이도는 1 이상이어야 합니다.")
        @Max(value = 25, message = "난이도는 25 이하여야 합니다.")
        Integer difficulty,

        @Schema(description = "제출 허용 언어 (서버 지원 언어 이름 그대로 - C, C++, Java, Python 3, JavaScript, TypeScript). 비우면 제한 없음. 지원하지 않는 이름이 섞이면 400", example = "[\"C\"]")
        @Size(max = 10, message = "허용 언어는 10개 이하여야 합니다.")
        List<String> allowedLanguages
) {
}
