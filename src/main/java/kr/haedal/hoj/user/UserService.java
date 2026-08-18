package kr.haedal.hoj.user;

import kr.haedal.hoj.common.error.InvalidInputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    /** users.login_id, users.name 컬럼 길이 (User 엔티티의 @Column(length = 50)) */
    public static final int MAX_LOGIN_ID_LENGTH = 50;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * loginId로 찾고, 없으면 MEMBER로 만든다.
     * 스텁 로그인(첫 로그인)과 운영진/수강생 배정(아직 로그인한 적 없는 부원을 loginId로 등록)이 공용으로 쓴다.
     * 이름은 loginId로 임시 채운다 — 홈페이지 연동 후 실제 이름으로 갱신된다.
     */
    public User findOrCreateMember(String loginId) {
        if (loginId == null || loginId.isBlank() || loginId.length() > MAX_LOGIN_ID_LENGTH) {
            // 경로 변수로 들어오는 loginId(운영진 지정 등)는 Bean Validation 을 거치지 않으므로 여기서 한 번 더 막는다 (DB 제약 위반 500 방지)
            throw new InvalidInputException("loginId: 1~" + MAX_LOGIN_ID_LENGTH + "자여야 합니다.");
        }
        return userRepository.findByLoginId(loginId)
                .orElseGet(() -> userRepository.save(User.member(loginId, loginId)));
    }
}
