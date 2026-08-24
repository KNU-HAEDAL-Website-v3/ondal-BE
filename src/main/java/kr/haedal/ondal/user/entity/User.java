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

    @Column(nullable = false, updatable = false)
    private Instant createdAt; // 저장은 UTC(Instant), 표시는 프론트에서 KST (docs 결정)

    protected User() {
        // JPA 스펙이 요구하는 기본 생성자. 외부에서 못 쓰게 protected.
    }

    private User(String loginId, String name, GlobalRole globalRole) {
        this.loginId = loginId;
        this.name = name;
        this.globalRole = globalRole;
        this.createdAt = Instant.now();
    }

    /** 일반 부원 생성 - UserService.findOrCreateMember(스텁 로그인·운영진/수강생 배정 공용)와 실제 홈페이지 첫 로그인이 쓴다. */
    public static User member(String loginId, String name) {
        return new User(loginId, name, GlobalRole.MEMBER);
    }

    /** 관리자 생성 - 부트스트랩(시더 또는 수동 SQL) 전용. 일반 코드 경로에서 호출 금지. */
    public static User admin(String loginId, String name) {
        return new User(loginId, name, GlobalRole.ADMIN);
    }

    public boolean isAdmin() {
        return globalRole == GlobalRole.ADMIN;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getName() { return name; }
    public GlobalRole getGlobalRole() { return globalRole; }
    public Instant getCreatedAt() { return createdAt; }
}
