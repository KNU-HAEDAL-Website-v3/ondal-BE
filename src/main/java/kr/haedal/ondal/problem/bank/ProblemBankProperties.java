package kr.haedal.ondal.problem.bank;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ondal.problem-bank.* - 문제 은행 레포(GitHub, 비공개)에서 문제를 직접 가져오는 설정 (docs 결정 11 보완, 2026-09-19 PM).
 *
 * - repo: "소유자/이름" (기본 KNU-HAEDAL-Website-v3/ondal-problems), ref: 가져올 브랜치·태그 (기본 main)
 * - token: 그 레포만 읽을 수 있는 fine-grained PAT (Contents: Read-only). 비어 있으면 "미설정" - 화면은 파일 업로드만 안내
 * - api-url: GitHub API 주소. 테스트가 가짜 서버로 바꾼다
 * - async: 작업을 별도 스레드에서 돌리고 화면이 상태를 폴링 (프록시 시간 제한 회피). test 는 false
 */
@ConfigurationProperties(prefix = "ondal.problem-bank")
public record ProblemBankProperties(Github github, Boolean async) {

    /** 가져오기를 별도 스레드에서 돌리고 상태를 폴링하게 할지 - 기본 true. test 프로필만 false(같은 스레드에서 끝까지) */
    public boolean asyncOrDefault() {
        return async == null || async;
    }

    public record Github(String apiUrl, String repo, String ref, String token) {

        public boolean configured() {
            return token != null && !token.isBlank() && repo != null && !repo.isBlank();
        }

        public String refOrDefault() {
            return ref == null || ref.isBlank() ? "main" : ref;
        }

        public String apiUrlOrDefault() {
            String url = apiUrl == null || apiUrl.isBlank() ? "https://api.github.com" : apiUrl;
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }
}
