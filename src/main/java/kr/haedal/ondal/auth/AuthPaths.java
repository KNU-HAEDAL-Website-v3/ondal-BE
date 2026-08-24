package kr.haedal.ondal.auth;

/**
 * 로그인 없이 호출 가능한 /api 경로 - 단 하나의 목록.
 * WebConfig(인터셉터 제외 목록)와 AuthorizationMappingValidator(기동 시 검증)가 같이 참조한다.
 * 여기 없는 /api/** 는 전부 로그인 필수 + 권한 어노테이션 필수다.
 */
public final class AuthPaths {

    public static final String[] PUBLIC = {
            "/api/auth/login",   // 로그인 전이니 당연히 면제
            "/api/auth/logout",  // 만료된 세션으로 눌러도 조용히 성공해야 함
            "/api/health"        // 모니터링용
    };

    private AuthPaths() {
    }
}
