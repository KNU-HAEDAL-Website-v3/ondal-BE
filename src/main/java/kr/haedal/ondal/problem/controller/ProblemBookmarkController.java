package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.problem.service.ProblemBookmarkService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 북마크 API (docs hoj/api.md 7절) - 로그인한 누구나 자기 것만. 읽기는 목록·상세의 bookmarked */
@Tag(name = "Problem", description = "북마크 - PUT/DELETE 멱등, 읽기는 목록·상세의 bookmarked")
@RestController
@RequestMapping("/api/problems/{problemId}/bookmark")
public class ProblemBookmarkController {

    private final ProblemBookmarkService problemBookmarkService;

    public ProblemBookmarkController(ProblemBookmarkService problemBookmarkService) {
        this.problemBookmarkService = problemBookmarkService;
    }

    @Operation(summary = "북마크 추가 (멱등) - 이미 있어도 204. 없는 문제는 404")
    @LoginOnly
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable Long problemId, @LoginUser User user) {
        problemBookmarkService.add(problemId, user);
    }

    @Operation(summary = "북마크 해제 (멱등) - 없어도 204. 없는 문제는 404")
    @LoginOnly
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long problemId, @LoginUser User user) {
        problemBookmarkService.remove(problemId, user);
    }
}
