package kr.haedal.ondal.problem.bank;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kr.haedal.ondal.common.error.ConflictException;
import kr.haedal.ondal.common.error.ErrorResponse;
import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.ProblemBankFetchException;
import kr.haedal.ondal.common.error.ServiceUnavailableException;
import kr.haedal.ondal.problem.dto.ProblemBankSourceResponse;
import kr.haedal.ondal.problem.dto.ProblemBankSyncResult;
import kr.haedal.ondal.problem.dto.ProblemBankSyncStatus;
import kr.haedal.ondal.problem.dto.ProblemBankSyncStatus.State;
import kr.haedal.ondal.problem.dto.ProblemImportRequest;
import kr.haedal.ondal.problem.dto.ProblemImportRequest.ImportProblem;
import kr.haedal.ondal.problem.dto.ProblemImportResult;
import kr.haedal.ondal.problem.service.ProblemImportService;
import kr.haedal.ondal.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "깃허브에서 문제 가져오기" (관리자 전용) - 문제 은행 레포의 ref 를 서버가 직접 받아 번들 가져오기와 같은 규칙으로 넣는다.
 *
 * 흐름: 커밋 SHA 조회 → zip 다운로드 → problems/* 읽기(ProblemBankZipReader) → 요청 DTO 검증(파일 업로드와 같은 제약) →
 * ProblemImportService.importBundle. 200문제(요청 상한)씩 나눠 넣으므로 묶음마다 트랜잭션이다.
 *
 * **비동기 작업**: 수 초~수십 초 걸리는 일이라 요청 하나로 기다리면 프록시(nginx 60초·Cloudflare 100초)가 끊고 화면은 "연결할 수 없음"만 본다.
 * 그래서 start() 는 작업을 한 스레드에서 돌리고 바로 상태를 돌려주며, 화면은 status() 를 폴링한다. 동시에 하나만 돈다(두 번째는 409).
 * 상태는 메모리에만 - 서버가 재시작되면 IDLE. (test 프로필은 async=false 로 같은 스레드에서 끝까지 돌려 단언한다)
 */
@Service
public class ProblemBankSyncService {

    static final int CHUNK_SIZE = 200;
    private static final Logger log = LoggerFactory.getLogger(ProblemBankSyncService.class);

    private final ProblemBankProperties.Github github;
    private final boolean async;
    private final GithubProblemBankClient client;
    private final ProblemBankZipReader reader;
    private final ProblemImportService importService;
    private final Validator validator;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "problem-bank-sync");
        thread.setDaemon(true);
        return thread;
    });

    /** 현재(또는 마지막) 작업 - 필드는 작업 스레드가 쓰고 요청 스레드가 읽는다 */
    private final Job job = new Job();

    public ProblemBankSyncService(ProblemBankProperties properties, GithubProblemBankClient client, ProblemBankZipReader reader,
                                  ProblemImportService importService, Validator validator) {
        this.github = properties.github();
        this.async = properties.asyncOrDefault();
        this.client = client;
        this.reader = reader;
        this.importService = importService;
        this.validator = validator;
    }

    public ProblemBankSourceResponse source() {
        return new ProblemBankSourceResponse(github.configured(), github.repo(), github.refOrDefault());
    }

    public ProblemBankSyncStatus status() {
        return job.snapshot();
    }

    /** 작업 시작 - 이미 도는 중이면 409. 돌려주는 값은 시작 직후 상태(async=false 면 끝난 상태) */
    public ProblemBankSyncStatus start(boolean overwrite, User admin) {
        if (!github.configured()) {
            throw new ServiceUnavailableException("PROBLEM_BANK_NOT_CONFIGURED",
                    "서버에 문제 은행 레포 토큰(PROBLEM_BANK_GITHUB_TOKEN)이 설정돼 있지 않아요. 파일로 가져오기는 됩니다.");
        }
        synchronized (job) {
            if (job.state == State.RUNNING) {
                throw new ConflictException("이미 깃허브에서 가져오는 중이에요 (" + job.requestedBy + " 시작). 끝나면 다시 눌러 주세요.");
            }
            job.begin(overwrite, admin.getName());
        }
        if (async) {
            executor.submit(() -> run(overwrite, admin));
        } else {
            run(overwrite, admin);
        }
        return job.snapshot();
    }

    private void run(boolean overwrite, User admin) {
        try {
            long fetchStart = System.nanoTime();
            job.step("커밋 조회");
            String sha = client.commitSha();
            job.step("zip 다운로드");
            byte[] zip = client.downloadZip();
            job.step("레포 읽기");
            List<ImportProblem> problems = reader.read(zip);
            job.fetchMs = (System.nanoTime() - fetchStart) / 1_000_000;
            if (problems.isEmpty()) {
                throw new ProblemBankFetchException("레포에 problems/*/meta.json 이 하나도 없어요 - 브랜치(" + github.refOrDefault() + ")가 맞는지 확인해 주세요");
            }
            job.total = problems.size();

            long importStart = System.nanoTime();
            ProblemImportResult total = new ProblemImportResult(0, 0, 0, new ArrayList<>(), new ArrayList<>());
            for (int from = 0; from < problems.size(); from += CHUNK_SIZE) {
                List<ImportProblem> chunk = problems.subList(from, Math.min(from + CHUNK_SIZE, problems.size()));
                ProblemImportRequest request = new ProblemImportRequest(chunk, overwrite);
                validate(request);
                int offset = from;
                job.step("문제 넣는 중");
                total = merge(total, importService.importBundle(request, admin, done -> job.processed = offset + done));
            }
            job.importMs = (System.nanoTime() - importStart) / 1_000_000;
            job.finish(new ProblemBankSyncResult(github.repo(), github.refOrDefault(), sha, problems.size(), Instant.now(), total));
            log.info("문제 은행 가져오기 완료: {} 문제 (추가 {} 갱신 {} 건너뜀 {}) 받기 {}ms 넣기 {}ms",
                    problems.size(), total.created(), total.updated(), total.skipped(), job.fetchMs, job.importMs);
        } catch (RuntimeException e) {
            job.fail(errorOf(e));
            log.warn("문제 은행 가져오기 실패: {}", e.getMessage(), e);
        } catch (Error e) {   // OutOfMemoryError 등 - 작업이 조용히 멈추면 화면이 영원히 "진행 중"이라 원인만 남기고 다시 던진다
            job.fail(new ErrorResponse("PROBLEM_BANK_SYNC_FAILED", "가져오는 중 심각한 오류: " + e));
            log.error("문제 은행 가져오기 중 Error", e);
            throw e;
        }
    }

    private static ErrorResponse errorOf(RuntimeException e) {
        if (e instanceof ProblemBankFetchException) {
            return new ErrorResponse("PROBLEM_BANK_FETCH_FAILED", e.getMessage());
        }
        if (e instanceof InvalidInputException) {
            return new ErrorResponse("INVALID_INPUT", e.getMessage());
        }
        if (e instanceof ServiceUnavailableException su) {
            return new ErrorResponse(su.getCode(), su.getMessage());
        }
        return new ErrorResponse("PROBLEM_BANK_SYNC_FAILED", "가져오는 중 오류가 났어요: " + e);
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

    /** 작업 하나의 상태 - 화면 폴링용 스냅샷을 만든다 */
    private static final class Job {
        volatile State state = State.IDLE;
        volatile String step;
        volatile int processed;
        volatile int total;
        volatile Instant startedAt;
        volatile Instant finishedAt;
        volatile Long fetchMs;
        volatile Long importMs;
        volatile boolean overwrite;
        volatile String requestedBy;
        volatile ProblemBankSyncResult outcome;
        volatile ErrorResponse error;

        void begin(boolean overwrite, String requestedBy) {
            this.state = State.RUNNING;
            this.step = "시작";
            this.processed = 0;
            this.total = 0;
            this.startedAt = Instant.now();
            this.finishedAt = null;
            this.fetchMs = null;
            this.importMs = null;
            this.overwrite = overwrite;
            this.requestedBy = requestedBy;
            this.outcome = null;
            this.error = null;
        }

        void step(String step) {
            this.step = step;
        }

        void finish(ProblemBankSyncResult result) {
            this.outcome = result;
            this.processed = result.problemsInRepo();
            this.step = "완료";
            this.finishedAt = Instant.now();
            this.state = State.DONE;
        }

        void fail(ErrorResponse error) {
            this.error = error;
            this.step = "실패";
            this.finishedAt = Instant.now();
            this.state = State.FAILED;
        }

        ProblemBankSyncStatus snapshot() {
            return new ProblemBankSyncStatus(state, step, processed, total, startedAt, finishedAt, fetchMs, importMs,
                    overwrite, requestedBy, outcome, error);
        }
    }
}
