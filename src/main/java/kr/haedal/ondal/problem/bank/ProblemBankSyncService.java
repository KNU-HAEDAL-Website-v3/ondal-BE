package kr.haedal.ondal.problem.bank;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.ProblemBankFetchException;
import kr.haedal.ondal.common.error.ServiceUnavailableException;
import kr.haedal.ondal.problem.dto.ProblemBankSourceResponse;
import kr.haedal.ondal.problem.dto.ProblemBankSyncResult;
import kr.haedal.ondal.problem.dto.ProblemImportRequest;
import kr.haedal.ondal.problem.dto.ProblemImportRequest.ImportProblem;
import kr.haedal.ondal.problem.dto.ProblemImportResult;
import kr.haedal.ondal.problem.service.ProblemImportService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * "깃허브에서 문제 가져오기" (관리자 전용) - 문제 은행 레포의 ref 를 서버가 직접 받아 번들 가져오기와 같은 규칙으로 넣는다.
 *
 * 흐름: 커밋 SHA 조회 → zip 다운로드 → problems/* 읽기(ProblemBankZipReader) → 요청 DTO 검증(파일 업로드와 같은 제약) →
 * ProblemImportService.importBundle. 200문제(요청 상한)씩 나눠 넣으므로 묶음마다 트랜잭션이다 - 도중 실패 시 앞 묶음은 남고,
 * 다시 실행하면 같은 번호는 건너뛰어(overwrite=false) 이어진다. 파일 업로드 경로(FE 10문제 분할)와 같은 성질.
 */
@Service
public class ProblemBankSyncService {

    static final int CHUNK_SIZE = 200;

    private final ProblemBankProperties.Github github;
    private final GithubProblemBankClient client;
    private final ProblemBankZipReader reader;
    private final ProblemImportService importService;
    private final Validator validator;

    public ProblemBankSyncService(ProblemBankProperties properties, GithubProblemBankClient client, ProblemBankZipReader reader,
                                  ProblemImportService importService, Validator validator) {
        this.github = properties.github();
        this.client = client;
        this.reader = reader;
        this.importService = importService;
        this.validator = validator;
    }

    public ProblemBankSourceResponse source() {
        return new ProblemBankSourceResponse(github.configured(), github.repo(), github.refOrDefault());
    }

    public ProblemBankSyncResult importFromGithub(boolean overwrite, User admin) {
        if (!github.configured()) {
            throw new ServiceUnavailableException("PROBLEM_BANK_NOT_CONFIGURED",
                    "서버에 문제 은행 레포 토큰(PROBLEM_BANK_GITHUB_TOKEN)이 설정돼 있지 않아요. 파일로 가져오기는 됩니다.");
        }
        String sha = client.commitSha();
        List<ImportProblem> problems = reader.read(client.downloadZip());
        if (problems.isEmpty()) {
            throw new ProblemBankFetchException("레포에 problems/*/meta.json 이 하나도 없어요 - 브랜치(" + github.refOrDefault() + ")가 맞는지 확인해 주세요");
        }

        ProblemImportResult total = new ProblemImportResult(0, 0, 0, new ArrayList<>(), new ArrayList<>());
        for (int from = 0; from < problems.size(); from += CHUNK_SIZE) {
            List<ImportProblem> chunk = problems.subList(from, Math.min(from + CHUNK_SIZE, problems.size()));
            ProblemImportRequest request = new ProblemImportRequest(chunk, overwrite);
            validate(request);
            total = merge(total, importService.importBundle(request, admin));
        }
        return new ProblemBankSyncResult(github.repo(), github.refOrDefault(), sha, problems.size(), Instant.now(), total);
    }

    /** 파일 업로드는 컨트롤러의 @Valid 가 거르는 제약을 여기서도 똑같이 - 레포 파일이 규칙(본문 10000자·케이스 64KB 등)을 어기면 어느 문제인지 알려 준다 */
    private void validate(ProblemImportRequest request) {
        Set<ConstraintViolation<ProblemImportRequest>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<ProblemImportRequest> first = violations.stream()
                .min(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .orElseThrow();
        String path = first.getPropertyPath().toString();
        String where = problemNoOf(request, path);
        throw new InvalidInputException((where == null ? "" : "문제 " + where + ": ") + first.getMessage());
    }

    /** "problems[3].testCases[1].input" 같은 경로에서 문제 번호를 찾아 메시지에 붙인다 */
    private static String problemNoOf(ProblemImportRequest request, String path) {
        if (!path.startsWith("problems[")) {
            return null;
        }
        int end = path.indexOf(']');
        try {
            int index = Integer.parseInt(path.substring("problems[".length(), end));
            Integer no = request.problems().get(index).problemNo();
            return no == null ? "#" + index : String.valueOf(no);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ProblemImportResult merge(ProblemImportResult a, ProblemImportResult b) {
        List<String> tags = new ArrayList<>(a.createdTags());
        b.createdTags().stream().filter(t -> !tags.contains(t)).forEach(tags::add);
        List<Integer> nos = new ArrayList<>(a.problemNos());
        nos.addAll(b.problemNos());
        return new ProblemImportResult(a.created() + b.created(), a.updated() + b.updated(), a.skipped() + b.skipped(), tags, nos);
    }
}
