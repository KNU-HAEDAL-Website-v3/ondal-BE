package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.haedal.ondal.judge.dto.TestCaseRequest;

import java.util.List;

/**
 * 문제 번들 가져오기 (관리자) - 문제 은행 레포(ondal-problems)의 빌드 산출물(JSON)을 그대로 올린다.
 * 문제마다 본문·난이도·허용 언어·태그 이름·제한·테스트케이스가 한 덩어리. 태그는 이름으로 받고 없으면 만든다 - 관리자 전용 API 라 태그 어휘 권한과 같다.
 * 운영 서버는 OIDC 세션이라 스크립트로 넣을 수 없어 브라우저(관리자 화면 "문제 가져오기")로 올리는 경로가 필요했다.
 */
public record ProblemImportRequest(
        @Schema(description = "가져올 문제 목록 (한 번에 최대 200개)")
        @NotEmpty(message = "problems 는 비어 있을 수 없습니다.")
        @Size(max = 200, message = "한 번에 200문제까지 가져올 수 있습니다.")
        @Valid
        List<ImportProblem> problems,

        @Schema(description = "같은 번호의 문제가 이미 있을 때 - true 면 본문·난이도·언어·태그·제한·테스트케이스를 덮어쓴다, 생략·false 면 건너뛴다")
        Boolean overwrite   // 원시 boolean 이면 필드를 생략한 본문이 400(역직렬화 실패)이 된다 - TestCaseRequest.isPublic 과 같은 이유로 래퍼
) {
    public boolean overwriteFlag() {
        return Boolean.TRUE.equals(overwrite);
    }

    public record ImportProblem(
            @Schema(description = "문제 번호 - 번들이 정한다 (전역 유일, 1000 이상)", example = "1001")
            @NotNull(message = "problemNo 는 필수입니다.")
            @Min(value = 1000, message = "문제 번호는 1000 이상이어야 합니다.")
            Integer problemNo,

            @NotBlank(message = "문제 제목은 비어 있을 수 없습니다.")
            @Size(max = 200, message = "문제 제목은 200자 이하여야 합니다.")
            String title,

            @Schema(description = "문제 본문 (마크다운)")
            @Size(max = 10000, message = "문제 본문은 10000자 이하여야 합니다.")
            String description,

            @Schema(description = "난이도 1~25 ((대분류-1)*5+소분류). null = 미지정")
            @Min(value = 1, message = "난이도는 1 이상이어야 합니다.")
            @Max(value = 25, message = "난이도는 25 이하여야 합니다.")
            Integer difficulty,

            @Schema(description = "제출 허용 언어 (ondal.judge.languages 키). 비우면 제한 없음", example = "[\"C\"]")
            @Size(max = 10, message = "허용 언어는 10개 이하여야 합니다.")
            List<String> allowedLanguages,

            @Schema(description = "태그 이름 목록 - 없는 이름은 새로 만든다 (40자 이하)")
            @Size(max = 10, message = "태그는 10개 이하여야 합니다.")
            List<@NotBlank(message = "태그 이름은 비어 있을 수 없습니다.") @Size(max = 40, message = "태그 이름은 40자 이하여야 합니다.") String> tags,

            @Schema(description = "시간 제한(ms) - null 이면 서버 기본값")
            Integer timeLimitMs,

            @Schema(description = "메모리 제한(MB) - null 이면 서버 기본값")
            Integer memoryLimitMb,

            @Schema(description = "테스트케이스 - 저장 순서 = 실행 순서. 없거나 비면 자동 채점 없음")
            @Size(max = 50, message = "테스트케이스는 최대 50개입니다.")
            @Valid
            List<TestCaseRequest> testCases,

            @Schema(description = "정답 코드(참고 풀이, 선택) - 언어별 1개, 최대 6개. 배열이 있으면 통째 교체(빈 배열 = 모두 삭제), 필드가 없으면(null) 기존 것을 건드리지 않는다. 운영진 이상만 본다")
            @Size(max = 6, message = "정답 코드는 언어별 1개, 최대 6개입니다.")
            @Valid
            List<ProblemSolutionPayload> solutions
    ) {
    }
}
