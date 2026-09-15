package kr.haedal.ondal.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 문제 태그 - 알고리즘·자료구조 분류 (예: DP, 그래프, 이분탐색).
 *
 * 만들고 고치고 지우는 건 전역 ADMIN 만 (docs permissions.md).
 * 운영진이 자유로 만들 수 있게 하면 "DP / 다이나믹프로그래밍 / dp" 로 갈라져 분류가 쓸모없어진다 - 선택은 운영진, 어휘 관리는 관리자.
 */
@Entity
@Table(name = "tags", uniqueConstraints = @UniqueConstraint(name = "uk_tags_name", columnNames = "name"))
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {
    }

    private Tag(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public static Tag create(String name) {
        return new Tag(name);
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
