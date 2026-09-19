package kr.haedal.ondal.problem.service;

import kr.haedal.ondal.assignment.repository.AssignmentRepository;
import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.judge.engine.JudgeProperties;
import kr.haedal.ondal.judge.repository.JudgeResultRepository;
import kr.haedal.ondal.judge.repository.TestCaseRepository;
import kr.haedal.ondal.problem.dto.ProblemAssignedCount;
import kr.haedal.ondal.problem.dto.ProblemPayload;
import kr.haedal.ondal.problem.dto.ProblemResponse;
import kr.haedal.ondal.problem.dto.ProblemSummary;
import kr.haedal.ondal.problem.dto.TagResponse;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.entity.Tag;
import kr.haedal.ondal.problem.repository.ProblemRepository;
import kr.haedal.ondal.problem.repository.TagRepository;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 문제 라이브러리 (HOJ) - 분반과 무관한 문제의 CRUD·목록 (docs judge/design.md 결정 17, V7).
 *
 * - 조회는 로그인한 누구나: HOJ 는 "지금까지 만든 문제를 모아 보는 곳"이라 수강생도 봐야 한다 (2026-09-15 PM)
 * - 출제·수정·삭제는 운영진 이상(@OperatorAnywhere) - 문제는 분반에 속하지 않아 {cohortId} 로 판정할 수 없다
 * - 삭제는 배정·제출이 하나라도 있으면 409 - 조용히 지우면 남의 분반 과제가 사라진다
 *
 * 테스트케이스·실행 제한은 JudgeService(.../judge)가 맡는다 - 출제 화면은 문제 저장과 채점 설정 저장을 차례로 부른다.
 */
@Service
@Transactional
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final TagRepository tagRepository;
    private final AssignmentRepository assignmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final JudgeResultRepository judgeResultRepository;
    private final SubmissionRepository submissionRepository;
    private final CohortAuthorizer cohortAuthorizer;
    private final JudgeProperties judgeProperties;

    public ProblemService(ProblemRepository problemRepository, TagRepository tagRepository,
                          AssignmentRepository assignmentRepository, TestCaseRepository testCaseRepository,
                          JudgeResultRepository judgeResultRepository, SubmissionRepository submissionRepository,
                          CohortAuthorizer cohortAuthorizer, JudgeProperties judgeProperties) {
        this.problemRepository = problemRepository;
        this.tagRepository = tagRepository;
        this.assignmentRepository = assignmentRepository;
        this.testCaseRepository = testCaseRepository;
        this.judgeResultRepository = judgeResultRepository;
        this.submissionRepository = submissionRepository;
        this.cohortAuthorizer = cohortAuthorizer;
        this.judgeProperties = judgeProperties;
    }

    /**
     * 목록 - 번호 오름차순. tagIds 를 주면 그 태그를 **모두** 가진 문제만(AND).
     * 화면이 필요로 하는 집계(자동 채점 여부·배정 횟수·내 해결 여부)는 문제 수와 무관하게 쿼리 3번으로 붙인다.
     */
    @Transactional(readOnly = true)
    public List<ProblemSummary> findAll(List<Long> tagIds, User viewer) {
        List<Problem> problems = (tagIds == null || tagIds.isEmpty())
                ? problemRepository.findAllWithTags()
                : problemRepository.findAllWithTagsByIdIn(
                        problemRepository.findIdsHavingAllTags(tagIds, tagIds.stream().distinct().count()));
        if (problems.isEmpty()) {
            return List.of();
        }
        List<Long> ids = problems.stream().map(Problem::getId).toList();
        Set<Long> judged = new HashSet<>(testCaseRepository.findProblemIdsWithCases(ids));
        Map<Long, Long> assigned = assignmentRepository.countGroupedByProblemIdIn(ids).stream()
                .collect(Collectors.toMap(ProblemAssignedCount::problemId, ProblemAssignedCount::count));
        Set<Long> solved = new HashSet<>(judgeResultRepository.findSolvedProblemIdsByUserId(viewer.getId()));

        return problems.stream()
                .map(problem -> new ProblemSummary(
                        problem.getId(),
                        problem.getProblemNo(),
                        problem.getTitle(),
                        tagResponses(problem),
                        judged.contains(problem.getId()),
                        assigned.getOrDefault(problem.getId(), 0L).intValue(),
                        solved.contains(problem.getId()),
                        problem.getDifficulty(),
                        problem.allowedLanguageList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemResponse findOne(Long problemId, User viewer) {
        return toResponse(requireProblemWithTags(problemId), viewer);
    }

    public ProblemResponse create(ProblemPayload payload, User author) {
        Integer problemNo = resolveProblemNoForCreate(payload.problemNo());
        Problem problem = Problem.create(problemNo, payload.title().strip(), payload.description(), null, null, author);
        problem.replaceTags(resolveTags(payload.tagIds()));
        problem.updateBank(payload.difficulty(), normalizeLanguages(payload.allowedLanguages()));
        return toResponse(problemRepository.save(problem), author);
    }

    /** PUT 전체 교체 - 번호를 비우면 기존 번호 유지(이미 번호가 있는 리소스라 "비움 = 새로 받기"가 아니다) */
    public ProblemResponse update(Long problemId, ProblemPayload payload, User viewer) {
        Problem problem = requireProblemWithTags(problemId);
        if (payload.problemNo() != null && !payload.problemNo().equals(problem.getProblemNo())) {
            if (problemRepository.existsByProblemNoAndIdNot(payload.problemNo(), problemId)) {
                throw new ConflictException("이미 사용 중인 문제 번호입니다: " + payload.problemNo());
            }
            problem.renumber(payload.problemNo());
        }
        problem.update(payload.title().strip(), payload.description());
        problem.replaceTags(resolveTags(payload.tagIds()));
        problem.updateBank(payload.difficulty(), normalizeLanguages(payload.allowedLanguages()));
        return toResponse(problem, viewer);
    }

    /**
     * 삭제 - 배정(과제)이나 제출이 하나라도 있으면 409.
     * 문제는 여러 분반이 공유하므로, 조용히 지우면 남의 분반 과제와 그 제출 기록이 함께 사라진다.
     * 지울 수 있는 건 "만들어 놓고 한 번도 안 쓴 문제"뿐 - 테스트케이스·태그 연결만 정리하면 된다.
     */
    public void delete(Long problemId) {
        Problem problem = requireProblemWithTags(problemId);
        if (assignmentRepository.existsByProblemId(problemId)) {
            throw new ConflictException("이 문제가 배정된 과제가 있습니다. 과제를 먼저 지워야 문제를 삭제할 수 있습니다.");
        }
        if (submissionRepository.existsByProblemId(problemId)) {
            throw new ConflictException("이 문제에 연습 제출 기록이 있습니다. 기록이 남아 있는 문제는 삭제할 수 없습니다.");
        }
        judgeResultRepository.deleteAllByProblemId(problemId);
        testCaseRepository.deleteAllByProblemId(problemId);
        problem.replaceTags(List.of());   // problem_tags 연결 해제 (FK RESTRICT)
        problemRepository.delete(problem);
    }

    // ---- 내부 -------------------------------------------------------------------------------

    /** 채점 설정(JudgeService)·과제 배정(AssignmentService)이 문제를 집어올 때 쓰는 공용 조회 */
    @Transactional(readOnly = true)
    public Problem requireProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다."));
    }

    private Problem requireProblemWithTags(Long problemId) {
        return problemRepository.findWithTagsById(problemId)
                .orElseThrow(() -> new NotFoundException("문제를 찾을 수 없습니다."));
    }

    /** 등록: 비우면 자동 채번(최대+1, 1000 시작). 동시 충돌은 unique 제약이 최후 방어 (schema.md 결정 9) */
    private Integer resolveProblemNoForCreate(Integer requested) {
        if (requested == null) {
            return problemRepository.findMaxProblemNo().map(max -> max + 1).orElse(1000);
        }
        if (problemRepository.existsByProblemNo(requested)) {
            throw new ConflictException("이미 사용 중인 문제 번호입니다: " + requested);
        }
        return requested;
    }

    /** 없는 태그 id 가 섞이면 400 - 화면이 방금 지워진 태그를 들고 있을 수 있다 */
    private List<Tag> resolveTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = tagIds.stream().distinct().toList();
        List<Tag> found = tagRepository.findAllByIdIn(distinct);
        if (found.size() != distinct.size()) {
            throw new InvalidInputException("없는 태그가 있습니다. 목록을 새로고침한 뒤 다시 선택해 주세요.");
        }
        return found;
    }

    private List<TagResponse> tagResponses(Problem problem) {
        return problem.getTags().stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(TagResponse::of)
                .toList();
    }

    private ProblemResponse toResponse(Problem problem, User viewer) {
        Long id = problem.getId();
        return new ProblemResponse(
                id,
                problem.getProblemNo(),
                problem.getTitle(),
                problem.getDescription(),
                tagResponses(problem),
                judgeProperties.effectiveTimeLimitMs(problem.getTimeLimitMs()),
                judgeProperties.effectiveMemoryLimitMb(problem.getMemoryLimitMb()),
                testCaseRepository.countByProblemId(id) > 0,
                (int) assignmentRepository.countGroupedByProblemIdIn(List.of(id)).stream()
                        .mapToLong(ProblemAssignedCount::count).sum(),
                judgeResultRepository.findSolvedProblemIdsByUserId(viewer.getId()).contains(id),
                problem.getCreatedBy() == null ? null : problem.getCreatedBy().getName(),
                problem.getCreatedAt(),
                problem.getUpdatedAt(),
                cohortAuthorizer.isOperatorAnywhere(viewer),
                problem.getDifficulty(),
                problem.allowedLanguageList());
    }

    /**
     * 허용 언어 정리 - 공백·중복 제거, 서버 지원 언어(ondal.judge.languages)가 아니면 400. 빈 목록 = 제한 없음.
     * 언어 이름은 FE 셀렉트·채점 설정과 같은 문자열(C, C++, Java, Python 3, JavaScript, TypeScript)이다
     */
    List<String> normalizeLanguages(List<String> requested) {
        List<String> result = new java.util.ArrayList<>();
        if (requested == null) {
            return result;
        }
        for (String raw : requested) {
            String language = raw == null ? "" : raw.strip();
            if (language.isEmpty() || result.contains(language)) {
                continue;
            }
            if (!judgeProperties.supportsLanguage(language)) {
                List<String> supported = judgeProperties.languages() == null ? List.of() : List.copyOf(judgeProperties.languages().keySet());
                throw new InvalidInputException("지원하지 않는 언어입니다: " + language + " (지원: " + String.join(", ", supported) + ")");
            }
            result.add(language);
        }
        return result;
    }
}
