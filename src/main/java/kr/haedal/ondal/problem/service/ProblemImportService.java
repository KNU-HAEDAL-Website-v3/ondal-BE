package kr.haedal.ondal.problem.service;

import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.judge.dto.JudgeConfigRequest;
import kr.haedal.ondal.judge.service.JudgeService;
import kr.haedal.ondal.problem.dto.ProblemImportRequest;
import kr.haedal.ondal.problem.dto.ProblemImportRequest.ImportProblem;
import kr.haedal.ondal.problem.dto.ProblemImportResult;
import kr.haedal.ondal.problem.entity.Problem;
import kr.haedal.ondal.problem.entity.Tag;
import kr.haedal.ondal.problem.repository.ProblemRepository;
import kr.haedal.ondal.problem.repository.TagRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 문제 번들 가져오기 (관리자 전용) - 문제 은행 레포(ondal-problems)의 빌드 산출물을 한 트랜잭션으로 넣는다.
 *
 * - 번호(problemNo)가 키: 있으면 overwrite 에 따라 덮어쓰거나 건너뛰고, 없으면 만든다. 번들 안에 같은 번호가 둘이면 400
 * - 태그는 이름으로 - 없으면 만든다. 관리자 전용 API 라 태그 어휘 관리 권한(@AdminOnly)과 같은 수준
 * - 테스트케이스·제한은 JudgeService.saveConfig(통째 교체) 재사용 - 채점 규칙(상한·개수)이 화면 출제와 같다. 재채점은 하지 않는다
 * - 하나라도 실패하면 전부 되돌린다 - 반쯤 들어간 번들이 가장 골치 아프다
 */
@Service
@Transactional
public class ProblemImportService {

    private final ProblemRepository problemRepository;
    private final TagRepository tagRepository;
    private final ProblemService problemService;
    private final JudgeService judgeService;

    public ProblemImportService(ProblemRepository problemRepository, TagRepository tagRepository,
                                ProblemService problemService, JudgeService judgeService) {
        this.problemRepository = problemRepository;
        this.tagRepository = tagRepository;
        this.problemService = problemService;
        this.judgeService = judgeService;
    }

    public ProblemImportResult importBundle(ProblemImportRequest request, User admin) {
        Map<String, Tag> tagsByName = new LinkedHashMap<>();
        tagRepository.findAllByOrderByNameAsc().forEach(tag -> tagsByName.put(tag.getName(), tag));
        List<String> createdTags = new ArrayList<>();
        List<Integer> processed = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (ImportProblem item : request.problems()) {
            if (!seen.add(item.problemNo())) {
                throw new InvalidInputException("번들 안에 같은 문제 번호가 두 번 있습니다: " + item.problemNo());
            }
            Optional<Problem> existing = problemRepository.findByProblemNo(item.problemNo());
            if (existing.isPresent() && !request.overwriteFlag()) {
                skipped++;   // 건너뛰는 문제의 태그는 만들지 않는다 - 건너뛴 항목이 흔적을 남기면 안 된다
                continue;
            }
            List<Tag> tags = resolveOrCreate(item.tags(), tagsByName, createdTags);
            List<String> languages = problemService.normalizeLanguages(item.allowedLanguages());

            Problem problem;
            if (existing.isPresent()) {
                problem = existing.get();
                problem.update(item.title().strip(), item.description());
                updated++;
            } else {
                problem = problemRepository.save(Problem.create(item.problemNo(), item.title().strip(), item.description(), null, null, admin));
                created++;
            }
            problem.updateBank(item.difficulty(), languages);
            problem.replaceTags(tags);
            judgeService.saveConfig(problem.getId(), new JudgeConfigRequest(
                    item.timeLimitMs(), item.memoryLimitMb(), item.testCases() == null ? List.of() : item.testCases(), false));
            processed.add(item.problemNo());
        }
        return new ProblemImportResult(created, updated, skipped, createdTags, processed);
    }

    /** 태그 이름 → 엔티티. 없으면 만들고 createdTags 에 기록. 공백·중복은 무시 */
    private List<Tag> resolveOrCreate(List<String> names, Map<String, Tag> tagsByName, List<String> createdTags) {
        List<Tag> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String raw : names) {
            String name = raw == null ? "" : raw.strip();
            if (name.isEmpty()) {
                continue;
            }
            Tag tag = tagsByName.get(name);
            if (tag == null) {
                tag = tagRepository.save(Tag.create(name));
                tagsByName.put(name, tag);
                createdTags.add(name);
            }
            if (!result.contains(tag)) {
                result.add(tag);
            }
        }
        return result;
    }
}
