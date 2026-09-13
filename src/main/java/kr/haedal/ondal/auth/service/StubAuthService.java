package kr.haedal.ondal.auth.service;

import kr.haedal.ondal.auth.AuthMode;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * 가짜 문 - loginId만 받고 검증 없이 통과시킨다. (개발·테스트 전용: ondal.auth.mode=stub, 기본값)
 *
 * find-or-create(없으면 MEMBER로 생성)인 이유:
 * 실제 홈페이지 연동(OidcAuthService)에서도 "홈페이지에서 인증된 사람이 Ondal에 처음 오면
 * 로컬 User 레코드를 만들어준다"는 흐름은 동일하다.
 * 스텁은 '검증' 단계만 생략할 뿐, 나머지 흐름은 실물과 같게 유지한다.
 *
 * prod 프로필에서는 절대 뜨면 안 된다 - 설정 실수로 스텁이 운영에 올라가면 loginId 만 알면 누구로든 로그인되므로
 * 빈 생성 시점에 기동 자체를 막는다 (fail-closed). 운영은 ondal.auth.mode=oidc (application.yml prod 절).
 */
@Service
@ConditionalOnProperty(name = AuthMode.PROPERTY, havingValue = AuthMode.STUB, matchIfMissing = true)
public class StubAuthService {

    private final UserService userService;

    public StubAuthService(UserService userService, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(
                    "prod 프로필에서 스텁 인증(" + AuthMode.PROPERTY + "=" + AuthMode.STUB + ")은 금지 - "
                            + AuthMode.OIDC + " 로 설정하고 OIDC_ISSUER·OIDC_CLIENT_SECRET 을 .env 에 채울 것");
        }
        this.userService = userService;
    }

    public User login(String loginId) {
        return userService.findOrCreateMember(loginId);
    }
}
