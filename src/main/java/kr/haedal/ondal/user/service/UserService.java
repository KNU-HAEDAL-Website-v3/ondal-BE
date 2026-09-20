package kr.haedal.ondal.user.service;

import kr.haedal.ondal.common.error.InvalidInputException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.user.dto.UserDirectoryEntry;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.entity.UserStatus;
import kr.haedal.ondal.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    /** users.login_id, users.name 컬럼 길이 (User 엔티티의 @Column(length = 50)) */
    public static final int MAX_LOGIN_ID_LENGTH = 50;
    public static final int MAX_NAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    public UserService(UserRepository userRepository, EnrollmentRepository enrollmentRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * loginId로 찾고, 없으면 MEMBER(ACTIVE)로 만든다.
     * 스텁 로그인(첫 로그인)과 운영진/수강생 배정(아직 로그인한 적 없는 부원을 loginId로 등록)이 공용으로 쓴다.
     * 이름은 loginId로 임시 채운다 - 홈페이지 연동 후 실제 이름으로 갱신된다.
     * 승인 대기 없이 ACTIVE 인 이유: 운영진이 명단으로 넣었거나(선등록) 개발용 스텁이라 확인이 끝난 계정이다.
     * ※ 이미 있는 PENDING 계정을 돌려줄 수도 있다 - 배정 경로에서는 EnrollmentService 가 approve() 를 붙여 준다
     */
    public User findOrCreateMember(String loginId) {
        validateLoginId(loginId);
        return userRepository.findByLoginId(loginId)
                .orElseGet(() -> userRepository.save(User.member(loginId, loginId)));
    }

    /**
     * 홈페이지 로그인(OIDC) 성공 시 - loginId(= 홈페이지 username)로 찾거나 **승인 대기(PENDING)로** 만들고, 홈페이지가 알려준 이름으로 맞춘다.
     * 신원의 원본은 홈페이지(User 주석): 이름은 매 로그인마다 덮어쓰고, globalRole·status 는 Ondal 것이라 건드리지 않는다 (부트스트랩 ADMIN·승인 유지).
     * 이름 클레임이 없으면 loginId 로 채운다(스텁·배정과 같은 규칙). 50자를 넘는 이름은 잘라 저장 - DB 제약 위반 500 방지.
     * 처음 보는 계정이 PENDING 인 이유(docs 결정 10): 로그인만으로는 부원인지 알 수 없다 - 운영진 이상이 승인하거나 분반에 배정해야 열린다.
     */
    public User syncFromIdentity(String loginId, String nameOrNull) {
        return syncFromIdentity(loginId, nameOrNull, null);
    }

    /**
     * avatarUrlOrNull: ID 토큰 picture 클레임(구글 프로필 사진). 있으면 매 로그인마다 덮어쓰고(사진을 바꾸면 따라옴), 없으면 기존 값을 그대로 둔다.
     * https:// 로 시작하는 500자 이하 주소만 받는다 - 화면이 <img src> 로 바로 쓰기 때문
     */
    public User syncFromIdentity(String loginId, String nameOrNull, String avatarUrlOrNull) {
        validateLoginId(loginId);
        String name = (nameOrNull == null || nameOrNull.isBlank()) ? loginId : nameOrNull.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        String resolvedName = name;
        User user = userRepository.findByLoginId(loginId)
                .orElseGet(() -> userRepository.save(User.pending(loginId, resolvedName)));
        if (!name.equals(user.getName())) {
            user.rename(name);
        }
        String avatarUrl = safeAvatarUrl(avatarUrlOrNull);
        if (avatarUrl != null && !avatarUrl.equals(user.getAvatarUrl())) {
            user.updateAvatar(avatarUrl);
        }
        return user;
    }

    static final int MAX_AVATAR_URL_LENGTH = 500;

    static String safeAvatarUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.strip();
        if (url.isEmpty() || url.length() > MAX_AVATAR_URL_LENGTH || !url.startsWith("https://")) {
            return null;
        }
        return url;
    }

    /** 승인 (운영진 이상, 멱등) - 없는 사용자는 404 */
    public UserDirectoryEntry approve(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));
        user.approve();
        return UserDirectoryEntry.of(user, enrollmentRepository.findAllByUserIdWithCohort(user.getId()));
    }

    /**
     * 부원 목록 - 승인 대기가 먼저, 그 다음 최근 생성순. statusOrNull 로 거른다.
     * 동아리 규모(수백 명)라 페이징 없이 전부 돌려주고 검색은 화면에서 한다. 소속 요약은 쿼리 2번(사용자 전부 + 소속 전부)으로 조립
     */
    @Transactional(readOnly = true)
    public List<UserDirectoryEntry> directory(UserStatus statusOrNull) {
        Map<Long, List<Enrollment>> byUser = enrollmentRepository.findAllWithCohort().stream()
                .collect(Collectors.groupingBy(e -> e.getUser().getId()));
        return userRepository.findAll().stream()
                .filter(u -> statusOrNull == null || u.getStatus() == statusOrNull)
                .sorted(Comparator.comparing((User u) -> u.getStatus() == UserStatus.PENDING ? 0 : 1)
                        .thenComparing(User::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(User::getId, Comparator.reverseOrder()))
                .map(u -> UserDirectoryEntry.of(u, byUser.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank() || loginId.length() > MAX_LOGIN_ID_LENGTH) {
            // 경로 변수로 들어오는 loginId(운영진 지정 등)는 Bean Validation 을 거치지 않으므로 여기서 한 번 더 막는다 (DB 제약 위반 500 방지)
            throw new InvalidInputException("loginId: 1~" + MAX_LOGIN_ID_LENGTH + "자여야 합니다.");
        }
    }
}
