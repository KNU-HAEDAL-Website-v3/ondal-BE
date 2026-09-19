package kr.haedal.ondal.problem.bank;

import kr.haedal.ondal.common.error.ProblemBankFetchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * GitHub API 로 문제 은행 레포(비공개)를 읽는다 - 요청 2가지: ref 의 커밋 SHA, ref 의 zip 아카이브.
 *
 * - 인증은 fine-grained PAT(Contents: Read-only) 를 Bearer 로. 토큰은 오류 메시지·로그에 절대 싣지 않는다
 * - zipball 은 302 로 codeload 주소(임시 서명 URL)를 돌려준다. 리다이렉트를 자동으로 따라가면 Authorization 헤더가 다른 호스트로
 *   함께 넘어가므로 따르지 않고(Redirect.NEVER) Location 을 읽어 **인증 없이** 한 번 더 받는다
 * - 실패는 전부 ProblemBankFetchException(502) - 호출자는 HTTP 예외 종류를 알 필요가 없다
 */
@Component
public class GithubProblemBankClient {

    /** zip 이 수 MB 라 넉넉히. 이보다 오래 걸리면 GitHub 쪽 장애로 보고 실패시킨다 */
    static final Duration TIMEOUT = Duration.ofSeconds(60);
    /** 문제 100개 + 테스트케이스가 압축 시 수 MB - 이보다 크면 뭔가 잘못된 것(레포에 큰 파일이 섞임) */
    static final int MAX_ZIP_BYTES = 64 * 1024 * 1024;
    private static final String API_VERSION = "2022-11-28";

    private final ProblemBankProperties.Github github;
    private final RestClient restClient;

    public GithubProblemBankClient(ProblemBankProperties properties) {
        this.github = properties.github();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** ref(브랜치·태그) 가 지금 가리키는 커밋의 전체 SHA - 가져오기 결과에 남겨 어느 시점을 넣었는지 추적한다 */
    public String commitSha() {
        String url = github.apiUrlOrDefault() + "/repos/" + github.repo() + "/commits/" + github.refOrDefault();
        try {
            String sha = restClient.get().uri(url)
                    .headers(this::authHeaders)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github.sha")
                    .retrieve()
                    .body(String.class);
            if (sha == null || sha.isBlank()) {
                throw new ProblemBankFetchException("커밋 조회 응답이 비어 있어요: " + describe());
            }
            return sha.trim();
        } catch (RestClientResponseException e) {
            throw new ProblemBankFetchException(explain("커밋 조회", e.getStatusCode().value()), e);
        } catch (ResourceAccessException e) {
            throw new ProblemBankFetchException("GitHub 에 연결하지 못했어요 (" + describe() + "): " + e.getMessage(), e);
        }
    }

    /** ref 의 zip 아카이브 전체 - 최상위 폴더 "<소유자>-<레포>-<sha7>/" 아래에 레포 파일이 있다 */
    public byte[] downloadZip() {
        String url = github.apiUrlOrDefault() + "/repos/" + github.repo() + "/zipball/" + github.refOrDefault();
        try {
            ResponseEntity<byte[]> first = restClient.get().uri(url)
                    .headers(this::authHeaders)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body;
            if (first.getStatusCode().is3xxRedirection()) {
                URI location = first.getHeaders().getLocation();
                if (location == null) {
                    throw new ProblemBankFetchException("zip 다운로드 리다이렉트에 Location 이 없어요: " + describe());
                }
                body = restClient.get().uri(location).retrieve().body(byte[].class);   // 서명된 임시 주소 - 인증 헤더 없이
            } else {
                body = first.getBody();
            }
            if (body == null || body.length == 0) {
                throw new ProblemBankFetchException("zip 다운로드 응답이 비어 있어요: " + describe());
            }
            if (body.length > MAX_ZIP_BYTES) {
                throw new ProblemBankFetchException("zip 이 너무 커요 (" + body.length / 1024 / 1024 + "MB): " + describe());
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new ProblemBankFetchException(explain("zip 다운로드", e.getStatusCode().value()), e);
        } catch (ResourceAccessException e) {
            throw new ProblemBankFetchException("GitHub 에 연결하지 못했어요 (" + describe() + "): " + e.getMessage(), e);
        }
    }

    private void authHeaders(HttpHeaders headers) {
        headers.setBearerAuth(github.token());
        headers.set("X-GitHub-Api-Version", API_VERSION);
    }

    private String describe() {
        return github.repo() + "@" + github.refOrDefault();
    }

    private String explain(String step, int status) {
        String hint = switch (status) {
            case 401 -> "토큰이 잘못됐거나 만료됐어요 (PROBLEM_BANK_GITHUB_TOKEN)";
            case 403 -> "토큰에 이 레포의 Contents 읽기 권한이 없거나 API 한도를 넘었어요";
            case 404 -> "레포 이름이나 브랜치가 없거나 토큰이 그 레포에 접근할 수 없어요";
            default -> "GitHub 응답 " + status;
        };
        return step + " 실패 - " + hint + " (" + describe() + ")";
    }
}
