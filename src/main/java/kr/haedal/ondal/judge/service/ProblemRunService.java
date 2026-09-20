package kr.haedal.ondal.judge.service;

import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.common.error.TooManyRequestsException;
import kr.haedal.ondal.judge.dto.JudgeRunRequest;
import kr.haedal.ondal.judge.dto.JudgeRunResponse;
import kr.haedal.ondal.judge.dto.ProblemRunRequest;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.repository.ProblemRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * "내 입력으로 실행" (docs hoj/api.md 8절) - 로그인한 누구나. 기존 출제 도구 실행(JudgeService.run)을 그대로 쓰되
 * 문제의 허용 언어 검사와 사용자별 분당 한도(RunRateLimiter)를 앞에 둔다. 저장 없음.
 * JudgeService 와 같은 이유로 트랜잭션 없이 돈다 - 엔진 대기(최대 30초) 동안 DB 연결을 잡고 있으면 안 된다.
 */
@Service
public class ProblemRunService {

    private final JudgeService judgeService;
    private final ProblemRepository problemRepository;
    private final RunRateLimiter rateLimiter;
    private final TransactionTemplate readOnlyTx;

    public ProblemRunService(JudgeService judgeService, ProblemRepository problemRepository, RunRateLimiter rateLimiter,
                             PlatformTransactionManager transactionManager) {
        this.judgeService = judgeService;
        this.problemRepository = problemRepository;
        this.rateLimiter = rateLimiter;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    /** 없는 문제 404 → 허용 언어 밖 400 → 분당 한도 429 → 실행 (엔진 미연결·오류 503 은 JudgeService.run) */
    public JudgeRunResponse run(Long problemId, ProblemRunRequest request, User user) {
        String language = request.language().strip();
        Problem problem = readOnlyTx.execute(tx -> problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다.")));
        if (!problem.allowsLanguage(language)) {
            throw new InvalidInputException("이 문제는 " + String.join(", ", problem.allowedLanguageList()) + " 로만 실행할 수 있습니다.");
        }
        if (!rateLimiter.tryAcquire(user.getId())) {
            throw new TooManyRequestsException("실행은 1분에 " + RunRateLimiter.LIMIT_PER_MINUTE + "번까지예요. 잠시 뒤 다시 시도하세요.");
        }
        return judgeService.run(problemId, new JudgeRunRequest(language, request.sourceCode(), List.copyOf(request.inputs()), null, null, null));
    }
}
