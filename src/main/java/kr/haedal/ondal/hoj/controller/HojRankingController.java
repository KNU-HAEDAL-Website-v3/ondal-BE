package kr.haedal.ondal.hoj.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.hoj.dto.HojRankingResponse;
import kr.haedal.ondal.hoj.service.HojRankingService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 랭킹 API (docs hoj/api.md 4절) - 로그인 누구나. 푼 문제 수만, 점수·티어 없음 */
@Tag(name = "HOJ")
@RestController
@RequestMapping("/api/hoj/ranking")
public class HojRankingController {

    private final HojRankingService hojRankingService;

    public HojRankingController(HojRankingService hojRankingService) {
        this.hojRankingService = hojRankingService;
    }

    @Operation(summary = "랭킹 - solvedCount desc → lastSolvedAt asc → name asc, 같은 수는 같은 순위(1,1,3), 0개는 제외. cohortId 를 주면 그 분반 소속만(없는 분반 404), size 기본 100·최대 200. me = 요청자 순위(0개면 null)")
    @LoginOnly
    @GetMapping
    public HojRankingResponse ranking(@RequestParam(required = false) Long cohortId,
                                      @RequestParam(required = false) Integer size,
                                      @LoginUser User me) {
        return hojRankingService.ranking(cohortId, size, me);
    }
}
