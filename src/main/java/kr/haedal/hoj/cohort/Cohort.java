package kr.haedal.hoj.cohort;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.haedal.hoj.common.error.CohortArchivedException;

import java.time.Instant;

/**
 * 분반(UI 용어) = Cohort(모델명). "2026-2 C언어"처럼 학기·트랙 단위로 열리는 반.
 * 소속(누가 이 반의 운영진/수강생인지)은 Enrollment가 담당한다 — 이 엔티티는 컬렉션 매핑을 갖지 않는다.
 *
 * 엔티티 규약 (이후 도메인이 그대로 따른다):
 * - protected 기본 생성자(JPA용) + private 전체 생성자 + 정적 팩토리 create(...)
 * - setter 없음. 상태 변경은 의미 있는 이름의 도메인 메서드로만
 * - enum은 STRING, 시각은 Instant(UTC)
 */
@Entity
@Table(name = "cohorts")
public class Cohort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CohortStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Cohort() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Cohort(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = CohortStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public static Cohort create(String name, String description) {
        return new Cohort(name, description);
    }

    /** PUT 전체 교체 — 이름·설명을 한 번에 바꾼다 */
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** 멱등 — 이미 보관이어도 예외 없음 */
    public void archive() {
        this.status = CohortStatus.ARCHIVED;
    }

    /** 멱등 — 이미 활성이어도 예외 없음 */
    public void restore() {
        this.status = CohortStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == CohortStatus.ACTIVE;
    }

    public boolean isArchived() {
        return status == CohortStatus.ARCHIVED;
    }

    /**
     * 분반 스코프의 모든 "쓰기" 서비스 메서드는 첫 줄에서 이걸 호출한다.
     * 보관 여부는 권한(403)이 아니라 도메인 규칙(409)이다.
     */
    public void ensureActive() {
        if (isArchived()) {
            throw new CohortArchivedException();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CohortStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
