package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.auth.authorization.OperatorAnywhere;
import kr.haedal.ondal.problem.dto.ProblemPayload;
import kr.haedal.ondal.problem.dto.ProblemResponse;
import kr.haedal.ondal.problem.dto.ProblemSummary;
import kr.haedal.ondal.problem.service.ProblemService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 문제 라이브러리 API (HOJ, #52~#56) - 분반에 속하지 않는 리소스라 경로에 {cohortId} 가 없다.
 * 조회는 로그인한 누구나, 출제·수정·삭제는 운영진 이상(@OperatorAnywhere).
 */
@Tag(name = "Problem", description = "문제 라이브러리(HOJ) - 조회는 누구나, 출제·수정·삭제는 운영진 이상")
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @Operation(summary = "문제 목록 - 번호 오름차순. tagIds 를 주면 그 태그를 모두 가진 문제만(AND)")
    @LoginOnly
    @GetMapping
    public List<ProblemSummary> list(@RequestParam(required = false) List<Long> tagIds, @LoginUser User user) {
        return problemService.findAll(tagIds, user);
    }

    @Operation(summary = "문제 상세 - 본문·태그·제한. 비공개 테스트케이스는 여기 없다(운영진은 .../judge)")
    @LoginOnly
    @GetMapping("/{problemId}")
    public ProblemResponse get(@PathVariable Long problemId, @LoginUser User user) {
        return problemService.findOne(problemId, user);
    }

    @Operation(summary = "[운영진] 문제 출제 - 번호를 비우면 자동 채번. 테스트케이스는 이어서 PUT .../judge")
    @OperatorAnywhere
    @PostMapping
    public ResponseEntity<ProblemResponse> create(@RequestBody @Valid ProblemPayload request, @LoginUser User user) {
        ProblemResponse created = problemService.create(request, user);
        return ResponseEntity.created(URI.create("/api/problems/" + created.id())).body(created);
    }

    @Operation(summary = "[운영진] 문제 수정 (전체 교체) - 번호를 비우면 기존 번호 유지. 태그는 준 목록으로 통째 교체")
    @OperatorAnywhere
    @PutMapping("/{problemId}")
    public ProblemResponse update(@PathVariable Long problemId,
                                  @RequestBody @Valid ProblemPayload request,
                                  @LoginUser User user) {
        return problemService.update(problemId, request, user);
    }

    @Operation(summary = "[운영진] 문제 삭제 - 배정된 과제나 연습 제출이 하나라도 있으면 409")
    @OperatorAnywhere
    @DeleteMapping("/{problemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long problemId) {
        problemService.delete(problemId);
    }
}
