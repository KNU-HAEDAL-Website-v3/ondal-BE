package kr.haedal.ondal.problem.bank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import kr.haedal.ondal.common.error.ProblemBankFetchException;
import kr.haedal.ondal.judge.dto.TestCaseRequest;
import kr.haedal.ondal.problem.dto.ProblemImportRequest.ImportProblem;
import kr.haedal.ondal.problem.dto.ProblemSolutionPayload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 문제 은행 레포의 zip 아카이브 → 가져오기 항목(ImportProblem). 레포의 tools/build.py 와 같은 규칙 - 서버가 직접 읽으므로 로컬 빌드가 필요 없다.
 *
 * 문제 폴더 problems/<이름>/ 의 파일:
 * - meta.json: problemNo·title·difficulty·allowedLanguages·tags·timeLimitMs·memoryLimitMb
 * - problem.md: 본문(마크다운). CRLF → LF, 앞뒤 공백 제거
 * - tests/NN.in + NN.out 쌍 = 테스트케이스(이름순), tests/public.txt 에 적힌 NN 은 공개
 * - solutions/sol.<ext> = 정답 코드(참고 풀이, 운영진만 열람) - 언어는 확장자로 (SOLUTION_LANGUAGES). 모르는 확장자·빈 파일은 무시
 * 그 밖의 파일(gen.py·README 등)은 무시한다. meta.json 이 없는 폴더는 문제가 아니다.
 */
@Component
public class ProblemBankZipReader {

    /** 문제 파일 하나의 상한 - 본문 10000자·테스트케이스 64KB 규칙보다 넉넉하게. 이보다 크면 엉뚱한 파일 */
    static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    /** solutions/sol.<ext> 의 확장자 → 정답 코드 언어 (docs hoj/api.md 6절) - 레포 tools/build.py 와 같은 표 */
    static final Map<String, String> SOLUTION_LANGUAGES = Map.of(
            "py", "Python 3", "c", "C", "cpp", "C++", "cc", "C++", "java", "Java", "js", "JavaScript", "ts", "TypeScript");
    private static final String PROBLEMS_DIR = "problems/";
    private static final String SOLUTION_PREFIX = "solutions/sol.";

    private final ObjectMapper objectMapper;

    public ProblemBankZipReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ImportProblem> read(byte[] zip) {
        Map<String, Map<String, String>> folders = collectProblemFolders(zip);
        List<ImportProblem> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> folder : folders.entrySet()) {
            Map<String, String> files = folder.getValue();
            if (!files.containsKey("meta.json")) {
                continue;
            }
            problems.add(toImportProblem(folder.getKey(), files));
        }
        problems.sort(Comparator.comparing(ImportProblem::problemNo));
        return problems;
    }

    /** problems/<폴더>/<상대 경로> → 내용. zip 최상위 폴더("소유자-레포-sha7/")는 벗겨낸다 */
    private Map<String, Map<String, String>> collectProblemFolders(byte[] zip) {
        Map<String, Map<String, String>> folders = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                int firstSlash = path.indexOf('/');
                String inRepo = firstSlash < 0 ? path : path.substring(firstSlash + 1);   // 최상위 폴더 제거
                if (!inRepo.startsWith(PROBLEMS_DIR)) {
                    continue;
                }
                String rest = inRepo.substring(PROBLEMS_DIR.length());
                int slash = rest.indexOf('/');
                if (slash < 0) {
                    continue;   // problems/ 바로 아래 파일 - 문제 아님
                }
                String folder = rest.substring(0, slash);
                String relative = rest.substring(slash + 1);
                if (!isProblemFile(relative)) {
                    continue;
                }
                byte[] bytes = in.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new ProblemBankFetchException("파일이 너무 커요 (2MB 초과): " + inRepo);
                }
                folders.computeIfAbsent(folder, k -> new LinkedHashMap<>())
                        .put(relative, new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new ProblemBankFetchException("zip 을 읽지 못했어요: " + e.getMessage(), e);
        }
        return folders;
    }

    private static boolean isProblemFile(String relative) {
        return relative.equals("meta.json") || relative.equals("problem.md")
                || (relative.startsWith("tests/") && (relative.endsWith(".in") || relative.endsWith(".out") || relative.equals("tests/public.txt")))
                || relative.startsWith(SOLUTION_PREFIX);
    }

    private ImportProblem toImportProblem(String folder, Map<String, String> files) {
        Meta meta;
        try {
            meta = objectMapper.readValue(files.get("meta.json"), Meta.class);
        } catch (RuntimeException e) {
            throw new ProblemBankFetchException("problems/" + folder + "/meta.json 을 읽지 못했어요: " + e.getMessage(), e);
        }
        if (meta.problemNo() == null) {
            throw new ProblemBankFetchException("problems/" + folder + "/meta.json 에 problemNo 가 없어요");
        }
        String description = files.containsKey("problem.md") ? files.get("problem.md").replace("\r\n", "\n").strip() : null;

        Set<String> publicNames = new HashSet<>();
        String publicText = files.get("tests/public.txt");
        if (publicText != null) {
            for (String token : publicText.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    publicNames.add(token);
                }
            }
        }
        List<TestCaseRequest> testCases = new ArrayList<>();
        for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {   // 이름순 = 실행 순서
            String path = file.getKey();
            if (!path.startsWith("tests/") || !path.endsWith(".in")) {
                continue;
            }
            String name = path.substring("tests/".length(), path.length() - ".in".length());
            String output = files.get("tests/" + name + ".out");
            if (output == null) {
                throw new ProblemBankFetchException("problems/" + folder + "/tests/" + name + ".out 이 없어요");
            }
            testCases.add(new TestCaseRequest(file.getValue(), output, publicNames.contains(name)));
        }

        // 정답 코드 - solutions/sol.<ext>, 언어별 1개. sol.cc 와 sol.cpp 가 같이 있으면 이름순 첫 파일이 C++ 이다
        Map<String, String> solutionsByLanguage = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {
            String path = file.getKey();
            if (!path.startsWith(SOLUTION_PREFIX)) {
                continue;
            }
            String language = SOLUTION_LANGUAGES.get(path.substring(SOLUTION_PREFIX.length()).toLowerCase(Locale.ROOT));
            String code = file.getValue().replace("\r\n", "\n");
            if (language == null || code.isBlank()) {
                continue;   // 모르는 확장자·빈 파일은 정답 코드가 아니다
            }
            solutionsByLanguage.putIfAbsent(language, code);
        }
        List<ProblemSolutionPayload> solutions = solutionsByLanguage.entrySet().stream()
                .map(entry -> new ProblemSolutionPayload(entry.getKey(), entry.getValue()))
                .toList();

        return new ImportProblem(
                meta.problemNo(),
                meta.title() == null ? null : meta.title().strip(),
                description,
                meta.difficulty(),
                meta.allowedLanguages() == null ? List.of() : meta.allowedLanguages(),
                meta.tags() == null ? List.of() : meta.tags(),
                meta.timeLimitMs(),
                meta.memoryLimitMb(),
                testCases,
                solutions);
    }

    /** meta.json - 레포 README 의 형식. 모르는 키는 무시(레포 쪽에 필드가 먼저 늘어도 서버가 깨지지 않게) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(Integer problemNo, String title, Integer difficulty, List<String> allowedLanguages, List<String> tags,
                Integer timeLimitMs, Integer memoryLimitMb) {
    }
}
