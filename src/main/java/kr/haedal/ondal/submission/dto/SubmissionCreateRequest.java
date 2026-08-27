package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.haedal.ondal.submission.entity.SubmissionType;

import java.util.List;

/**
 * 제출 요청의 request JSON 파트 - 파일은 별도 multipart 파트(file)로 받는다.
 * type별 필수·금지 조합(CODE=codeText·language, FILE=file 파트, LINK=linkUrls 1~5개)은
 * 서비스가 검증한다 - 어노테이션으로는 파트 간 관계를 못 본다.
 */
public record SubmissionCreateRequest(
        @Schema(description = "제출 형태 - CODE(코드) / FILE(zip) / LINK(링크). 3종 택1")
        @NotNull(message = "제출 형태는 필수입니다.")
        SubmissionType type,

        @Schema(description = "코드 텍스트 - CODE 필수, 다른 형태에 실리면 400")
        @Size(max = 100000, message = "코드는 100000자 이하여야 합니다.")
        String codeText,

        @Schema(description = "제출 언어(예: Python 3) - CODE 필수. 하이라이팅 표시 + 채점(P2) 언어 식별", example = "C")
        @Size(max = 30, message = "제출 언어는 30자 이하여야 합니다.")
        String language,

        @Schema(description = "링크 URL 목록 - LINK 필수(1~5개, 입력 순서 보존), 다른 형태에 실리면 400")
        @Size(max = 5, message = "링크는 최대 5개까지입니다.")
        List<@Size(max = 2048, message = "링크는 2048자 이하여야 합니다.") String> linkUrls
) {
}
