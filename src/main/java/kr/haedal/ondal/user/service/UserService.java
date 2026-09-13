package kr.haedal.ondal.user.service;

import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;

import kr.haedal.ondal.common.error.InvalidInputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    /** users.login_id, users.name 컬럼 길이 (User 엔티티의 @Column(length = 50)) */
    public static final int MAX_LOGIN_ID_LENGTH = 50;
    public static final int MAX_NAME_LENGTH = 50;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * loginId로 찾고, 없으면 MEMBER로 만든다.
     * 스텁 로그인(첫 로그인)과 운영진/수강생 배정(아직 로그인한 적 없는 부원을 loginId로 등록)이 공용으로 쓴다.
     * 이름은 loginId로 임시 채운다 - 홈페이지 연동 후 실제 이름으로 갱신된다.
     */
    public User findOrCreateMember(String loginId) {
        if (loginId == null || loginId.isBlank() || loginId.length() > MAX_LOGIN_ID_LENGTH) {
            // 경로 변수로 들어오는 loginId(운영진 지정 등)는 Bean Validation 을 거치지 않으므로 여기서 한 번 더 막는다 (DB 제약 위반 500 방지)
            throw new InvalidInputException("loginId: 1~" + MAX_LOGIN_ID_LENGTH + "자여야 합니다.");
        }
        return userRepository.findByLoginId(loginId)
                .orElseGet(() -> userRepository.save(User.member(loginId, loginId)));
    }

    /**
     * 홈페이지 로그인(OIDC) 성공 시 - loginId(= 홈페이지 username)로 찾거나 만들고, 홈페이지가 알려준 이름으로 맞춘다.
     * 신원의 원본은 홈페이지(User 주석): 이름은 매 로그인마다 덮어쓰고, globalRole 은 Ondal 것이라 건드리지 않는다 (부트스트랩 ADMIN 유지).
     * 이름 클레임이 없으면 loginId 로 채운다(스텁·배정과 같은 규칙). 50자를 넘는 이름은 잘라 저장 - DB 제약 위반 500 방지.
     */
    public User syncFromIdentity(String loginId, String nameOrNull) {
        User user = findOrCreateMember(loginId);
        String name = (nameOrNull == null || nameOrNull.isBlank()) ? loginId : nameOrNull.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        if (!name.equals(user.getName())) {
            user.rename(name);
        }
        return user;
    }
}
