package kr.haedal.ondal.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.problem.entity.ProblemSolution;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/** 정답 코드 1건 응답 (운영진 이상) - GET 과 PUT 의 응답이 같은 모양, 언어 이름순 */
public record ProblemSolutionResponse(
        String language,
        String codeText,
        @Schema(description = "마지막으로 저장한 사람 - 가져오기면 요청한 관리자") UserSummary updatedBy,
        Instant updatedAt
) {
    /** updatedBy 가 fetch join 돼 있어야 한다 (findAllByProblemIdWithUpdatedBy) */
    public static ProblemSolutionResponse from(ProblemSolution solution) {
        return new ProblemSolutionResponse(solution.getLanguage(), solution.getCodeText(),
                UserSummary.of(solution.getUpdatedBy(), null),   // 문제는 분반 문맥이 없다 - 직책은 전역 역할로만
                solution.getUpdatedAt());
    }
}
