package kr.haedal.hoj.auth;

import kr.haedal.hoj.user.User;
import kr.haedal.hoj.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가짜 문 — loginId만 받고 검증 없이 통과시킨다. (개발 전용)
 *
 * find-or-create(없으면 MEMBER로 생성)인 이유:
 * 실제 홈페이지 연동에서도 "홈페이지에서 인증된 사람이 HOJ에 처음 오면
 * 로컬 User 레코드를 만들어준다"는 흐름은 동일하다.
 * 스텁은 '검증' 단계만 생략할 뿐, 나머지 흐름은 실물과 같게 유지한다.
 */
@Service
@Transactional
public class StubAuthService implements AuthService {

    private final UserRepository userRepository;

    public StubAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User login(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseGet(() -> userRepository.save(User.member(loginId, loginId)));
    }
}
