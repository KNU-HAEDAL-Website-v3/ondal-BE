package kr.haedal.ondal.problem.service;

import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.judge.engine.JudgeProperties;
import kr.haedal.ondal.problem.dto.ProblemSolutionPayload;
import kr.haedal.ondal.problem.dto.ProblemSolutionResponse;
import kr.haedal.ondal.problem.dto.ProblemSolutionsRequest;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.entity.ProblemSolution;
import kr.haedal.ondal.problem.repository.ProblemSolutionRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정답 코드(참고 풀이) - 문제마다 언어별 1건, 운영진 이상 전용 (V11, docs 결정 13, hoj/api.md 6절).
 *
 * - 학생에게는 존재도 보이지 않는다: 이 서비스의 읽기는 @OperatorAnywhere 컨트롤러와 상세의 solutionLanguages(운영진에게만) 에서만 부른다
 * - 저장은 통째 교체 - 채점 설정(JudgeService.saveConfig) 과 같은 규약. 화면의 언어 탭 묶음이 곧 결과
 * - 번들·깃허브 가져오기(ProblemImportService) 도 같은 replace 를 탄다 - 검증(지원 언어·중복)이 한 곳
 */
@Service
@Transactional
public class ProblemSolutionService {

    /** 언어별 1개 - 지원 언어 수와 같다 */
    public static final int MAX_SOLUTIONS = 6;

    private final ProblemSolutionRepository problemSolutionRepository;
    private final ProblemService problemService;
    private final JudgeProperties judgeProperties;

    public ProblemSolutionService(ProblemSolutionRepository problemSolutionRepository, ProblemService problemService,
                                  JudgeProperties judgeProperties) {
        this.problemSolutionRepository = problemSolutionRepository;
        this.problemService = problemService;
        this.judgeProperties = judgeProperties;
    }

    @Transactional(readOnly = true)
    public List<ProblemSolutionResponse> findAll(Long problemId) {
        problemService.requireProblem(problemId);
        return problemSolutionRepository.findAllByProblemIdWithUpdatedBy(problemId).stream()
                .map(ProblemSolutionResponse::from)
                .toList();
    }

    /** PUT 통째 교체 - 응답은 GET 과 같다 */
    public List<ProblemSolutionResponse> replaceAll(Long problemId, ProblemSolutionsRequest request, User by) {
        Problem problem = problemService.requireProblem(problemId);
        replace(problem, request.solutions(), by);
        return problemSolutionRepository.findAllByProblemIdWithUpdatedBy(problemId).stream()
                .map(ProblemSolutionResponse::from)
                .toList();
    }

    /**
     * 기존 정답 코드를 전부 지우고 준 목록으로 다시 넣는다 - 가져오기(ProblemImportService)가 호출자 트랜잭션 안에서 함께 쓴다.
     * 지원하지 않는 언어·같은 언어 둘 → 400. 언어 이름은 앞뒤 공백을 걷어 저장한다
     */
    public void replace(Problem problem, List<ProblemSolutionPayload> items, User by) {
        Map<String, String> byLanguage = new LinkedHashMap<>();
        if (items != null) {
            for (ProblemSolutionPayload item : items) {
                String language = item.language() == null ? "" : item.language().strip();
                if (!judgeProperties.supportsLanguage(language)) {
                    List<String> supported = judgeProperties.languages() == null ? List.of() : List.copyOf(judgeProperties.languages().keySet());
                    throw new InvalidInputException("지원하지 않는 언어입니다: " + language + " (지원: " + String.join(", ", supported) + ")");
                }
                if (byLanguage.putIfAbsent(language, item.codeText()) != null) {
                    throw new InvalidInputException("같은 언어의 정답 코드가 두 번 있습니다: " + language);
                }
            }
        }
        if (byLanguage.size() > MAX_SOLUTIONS) {
            throw new InvalidInputException("정답 코드는 언어별 1개, 최대 " + MAX_SOLUTIONS + "개까지 저장할 수 있습니다.");
        }
        problemSolutionRepository.deleteAllByProblemId(problem.getId());
        for (Map.Entry<String, String> entry : byLanguage.entrySet()) {
            problemSolutionRepository.save(ProblemSolution.create(problem, entry.getKey(), entry.getValue(), by));
        }
    }
}
