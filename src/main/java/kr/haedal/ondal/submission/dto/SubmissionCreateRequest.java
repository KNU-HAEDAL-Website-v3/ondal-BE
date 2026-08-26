package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 제출 요청의 request JSON 파트 - 파일은 별도 multipart 파트(file)로 받는다.
 * 필드 조합 규칙(본문은 코드/파일 택1, 본문 또는 링크 최소 1개)은 서비스가 검증한다 - 어노테이션으로는 파트 간 관계를 못 본다.
 */
public record SubmissionCreateRequest(
        @Schema(description = "본문·코드 - 붙여넣은 코드 텍스트 (선택). 파일과 동시 사용 불가")
        @Size(max = 100000, message = "코드는 100000자 이하여야 합니다.")
        String codeText,

        @Schema(description = "제출 언어 (선택, 예: Python 3) - 코드 제출일 때만. 표시·하이라이팅용", example = "C")
        @Size(max = 30, message = "제출 언어는 30자 이하여야 합니다.")
        String language,

        @Schema(description = "링크 - GitHub·배포 URL 등 (선택)")
        @Size(max = 2048, message = "링크는 2048자 이하여야 합니다.")
        String linkUrl
) {
}
