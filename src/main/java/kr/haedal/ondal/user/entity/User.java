package kr.haedal.ondal.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Ondal 사용자.
 * 홈페이지 연동 후에는 "홈페이지 계정의 로컬 사본" 역할을 한다 -
 * 신원의 원본(source of truth)은 홈페이지, Ondal은 loginId로 매칭만 한다.
 * 승인 상태(status)만은 Ondal 것이다 - "부원임을 운영진이 확인했는가"는 홈페이지가 모른다 (docs 결정 10).
 */
@Entity
@Table(name = "users") // "user"는 PostgreSQL 예약어라서 복수형 사용
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING) // ORDINAL 금지 - enum 순서 바뀌면 데이터가 깨진다
    @Column(nullable = false, length = 20)
    private GlobalRole globalRole;

    /** 승인 상태 - PENDING(첫 홈페이지 로그인, 승인 대기) / ACTIVE. V8. 되돌림 없음 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt; // 저장은 UTC(Instant), 표시는 프론트에서 KST (docs 결정)

    protected User() {
        // JPA 스펙이 요구하는 기본 생성자. 외부에서 못 쓰게 protected.
    }

    private User(String loginId, String name, GlobalRole globalRole, UserStatus status) {
        this.loginId = loginId;
        this.name = name;
        this.globalRole = globalRole;
        this.status = status;
        this.createdAt = Instant.now();
    }

    /**
     * 일반 부원 생성 - 바로 이용 가능(ACTIVE). UserService.findOrCreateMember(스텁 로그인·운영진/수강생 배정 선등록)가 쓴다.
     * 운영진이 명단으로 넣거나(선등록) 개발용 스텁으로 만든 계정이라 승인 절차가 필요 없다.
     */
    public static User member(String loginId, String name) {
        return new User(loginId, name, GlobalRole.MEMBER, UserStatus.ACTIVE);
    }

    /**
     * 승인 대기 부원 생성 - 실제 홈페이지(OIDC) 첫 로그인 전용 (UserService.syncFromIdentity).
     * 로그인만으로는 부원인지 알 수 없으므로 운영진 이상이 승인하거나 분반에 배정할 때까지 화면을 열지 않는다.
     */
    public static User pending(String loginId, String name) {
        return new User(loginId, name, GlobalRole.MEMBER, UserStatus.PENDING);
    }

    /** 관리자 생성 - 부트스트랩(시더 또는 수동 SQL) 전용. 일반 코드 경로에서 호출 금지. */
    public static User admin(String loginId, String name) {
        return new User(loginId, name, GlobalRole.ADMIN, UserStatus.ACTIVE);
    }

    public boolean isAdmin() {
        return globalRole == GlobalRole.ADMIN;
    }

    public boolean isPending() {
        return status == UserStatus.PENDING;
    }

    /** 승인 - 운영진 이상의 명시적 승인, 또는 분반 배정(운영진 지정·수강생 배정)에 딸려서. 멱등 */
    public void approve() {
        this.status = UserStatus.ACTIVE;
    }

    /** 홈페이지(신원의 원본)가 알려준 최신 이름으로 맞춘다 - 로그인 동기화(UserService.syncFromIdentity) 전용 */
    public void rename(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public GlobalRole getGlobalRole() { return globalRole; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
