package kr.haedal.ondal.hoj.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.hoj.dto.HojSubmissionFeedResponse;
import kr.haedal.ondal.hoj.service.HojSubmissionFeedService;
import kr.haedal.ondal.judge.entity.Verdict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 채점 현황 피드 API (docs hoj/api.md 2절) - 로그인 누구나, HOJ 연습 제출만 */
@Tag(name = "HOJ", description = "채점 현황·랭킹·사용자 페이지 - 로그인 누구나. 사람 정보는 id·이름·직책만")
@RestController
@RequestMapping("/api/hoj/submissions")
public class HojSubmissionController {

    private final HojSubmissionFeedService hojSubmissionFeedService;

    public HojSubmissionController(HojSubmissionFeedService hojSubmissionFeedService) {
        this.hojSubmissionFeedService = hojSubmissionFeedService;
    }

    @Operation(summary = "채점 현황 피드 - 연습 제출만, 최신(id) 먼저. problemId·userId·verdict·language 로 거르기, size 기본 50·최대 200, beforeId 커서(그보다 작은 id). 응답 nextBeforeId 가 null 이면 끝. codeText 없음")
    @LoginOnly
    @GetMapping
    public HojSubmissionFeedResponse feed(@RequestParam(required = false) Long problemId,
                                          @RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) Verdict verdict,
                                          @RequestParam(required = false) String language,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) Long beforeId) {
        return hojSubmissionFeedService.feed(problemId, userId, verdict, language, size, beforeId);
    }
}
