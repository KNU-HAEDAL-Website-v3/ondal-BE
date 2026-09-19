package kr.haedal.ondal.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.AdminOnly;
import kr.haedal.ondal.auth.authorization.LoginOnly;
import kr.haedal.ondal.auth.authorization.OperatorAnywhere;
import kr.haedal.ondal.problem.bank.ProblemBankSyncService;
import kr.haedal.ondal.problem.dto.ProblemBankSourceResponse;
import kr.haedal.ondal.problem.dto.ProblemBankSyncResult;
import kr.haedal.ondal.problem.dto.ProblemImportRequest;
import kr.haedal.ondal.problem.dto.ProblemImportResult;
import kr.haedal.ondal.problem.dto.ProblemPayload;
import kr.haedal.ondal.problem.dto.ProblemResponse;
import kr.haedal.ondal.problem.dto.ProblemSummary;
import kr.haedal.ondal.problem.service.ProblemImportService;
import kr.haedal.ondal.problem.service.ProblemService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 문제 라이브러리 API (HOJ, #52~#56) - 분반에 속하지 않는 리소스라 경로에 {cohortId} 가 없다.
 * 조회는 로그인한 누구나, 출제·수정·삭제는 운영진 이상(@OperatorAnywhere).
 */
@Tag(name = "Problem", description = "문제 라이브러리(HOJ) - 조회는 누구나, 출제·수정·삭제는 운영진 이상")
@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;
    private final ProblemImportService problemImportService;
    private final ProblemBankSyncService problemBankSyncService;

    public ProblemController(ProblemService problemService, ProblemImportService problemImportService,
                             ProblemBankSyncService problemBankSyncService) {
        this.problemService = problemService;
        this.problemImportService = problemImportService;
        this.problemBankSyncService = problemBankSyncService;
    }

    /** 문제 은행 레포(GitHub, 비공개)를 서버가 직접 읽는다 - 관리자 화면 "깃허브에서 가져오기". 로컬 빌드·파일 선택이 필요 없는 기본 경로 */
    @Operation(summary = "[관리자] 문제 은행 레포 설정 - 어느 레포·브랜치에서 가져오는지, 토큰이 설정돼 있는지(false 면 파일 업로드만 가능)")
    @AdminOnly
    @GetMapping("/import/github")
    public ProblemBankSourceResponse problemBankSource() {
        return problemBankSyncService.source();
    }

    @Operation(summary = "[관리자] 깃허브에서 문제 가져오기 - 레포 ref 의 zip 을 서버가 받아 problems/* 를 읽고 번들 가져오기와 같은 규칙(번호 키·overwrite·태그 생성·테스트케이스 교체)으로 넣는다. 결과에 커밋 SHA")
    @AdminOnly
    @PostMapping("/import/github")
    public ProblemBankSyncResult importFromGithub(@RequestParam(defaultValue = "false") boolean overwrite, @LoginUser User user) {
        return problemBankSyncService.importFromGithub(overwrite, user);
    }

    /** 문제 은행 레포(ondal-problems)의 빌드 산출물(JSON)을 관리자 화면에서 올린다 - 운영은 OIDC 세션이라 스크립트로 넣을 수 없다 */
    @Operation(summary = "[관리자] 문제 번들 가져오기 - 번호가 키. 같은 번호는 overwrite 에 따라 덮어쓰기/건너뛰기, 태그 이름은 없으면 생성, 테스트케이스는 통째 교체(재채점 없음). 하나라도 실패하면 전부 되돌림")
    @AdminOnly
    @PostMapping("/import")
    public ProblemImportResult importBundle(@RequestBody @Valid ProblemImportRequest request, @LoginUser User user) {
        return problemImportService.importBundle(request, user);
    }

    @Operation(summary = "문제 목록 - 번호 오름차순. tagIds 를 주면 그 태그를 모두 가진 문제만(AND)")
    @LoginOnly
    @GetMapping
    public List<ProblemSummary> list(@RequestParam(required = false) List<Long> tagIds, @LoginUser User user) {
        return problemService.findAll(tagIds, user);
    }

    @Operation(summary = "문제 상세 - 본문·태그·제한. 비공개 테스트케이스는 여기 없다(운영진은 .../judge)")
    @LoginOnly
    @GetMapping("/{problemId}")
    public ProblemResponse get(@PathVariable Long problemId, @LoginUser User user) {
        return problemService.findOne(problemId, user);
    }

    @Operation(summary = "[운영진] 문제 출제 - 번호를 비우면 자동 채번. 테스트케이스는 이어서 PUT .../judge")
    @OperatorAnywhere
    @PostMapping
    public ResponseEntity<ProblemResponse> create(@RequestBody @Valid ProblemPayload request, @LoginUser User user) {
        ProblemResponse created = problemService.create(request, user);
        return ResponseEntity.created(URI.create("/api/problems/" + created.id())).body(created);
    }

    @Operation(summary = "[운영진] 문제 수정 (전체 교체) - 번호를 비우면 기존 번호 유지. 태그는 준 목록으로 통째 교체")
    @OperatorAnywhere
    @PutMapping("/{problemId}")
    public ProblemResponse update(@PathVariable Long problemId,
                                  @RequestBody @Valid ProblemPayload request,
                                  @LoginUser User user) {
        return problemService.update(problemId, request, user);
    }

    @Operation(summary = "[운영진] 문제 삭제 - 배정된 과제나 연습 제출이 하나라도 있으면 409")
    @OperatorAnywhere
    @DeleteMapping("/{problemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long problemId) {
        problemService.delete(problemId);
    }
}
