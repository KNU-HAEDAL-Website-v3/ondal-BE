package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.OperatorAnywhere;
import kr.haedal.ondal.problem.dto.ProblemSolutionResponse;
import kr.haedal.ondal.problem.dto.ProblemSolutionsRequest;
import kr.haedal.ondal.problem.service.ProblemSolutionService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정답 코드(참고 풀이) API - 운영진 이상 전용 (docs hoj/api.md 6절). 학생에게는 존재 자체가 보이지 않는다(상세의 solutionLanguages 도 []).
 */
@Tag(name = "Problem", description = "정답 코드(참고 풀이) - 운영진 이상만, 언어별 1건, 통째 교체")
@RestController
@RequestMapping("/api/problems/{problemId}/solutions")
public class ProblemSolutionController {

    private final ProblemSolutionService problemSolutionService;

    public ProblemSolutionController(ProblemSolutionService problemSolutionService) {
        this.problemSolutionService = problemSolutionService;
    }

    @Operation(summary = "[운영진] 정답 코드 목록 - 언어 이름순, 코드 전문 포함")
    @OperatorAnywhere
    @GetMapping
    public List<ProblemSolutionResponse> list(@PathVariable Long problemId) {
        return problemSolutionService.findAll(problemId);
    }

    @Operation(summary = "[운영진] 정답 코드 저장 (통째 교체) - 빈 배열이면 모두 삭제. 최대 6개, 같은 언어 둘·지원하지 않는 언어는 400. 응답은 GET 과 같다")
    @OperatorAnywhere
    @PutMapping
    public List<ProblemSolutionResponse> replace(@PathVariable Long problemId,
                                                 @RequestBody @Valid ProblemSolutionsRequest request,
                                                 @LoginUser User user) {
        return problemSolutionService.replaceAll(problemId, request, user);
    }
}
