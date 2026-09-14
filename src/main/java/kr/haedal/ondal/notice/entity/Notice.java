package kr.haedal.ondal.notice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;

/**
 * 공지사항 - 두 종류를 한 테이블로 다룬다 (docs notice/design.md 결정 1).
 *   전체 공지: cohort 가 null. 관리자만 쓰고, 로그인한 누구나 본다
 *   분반 공지: cohort 가 있다. 그 분반 운영진 이상이 쓰고, 소속자·관리자가 본다
 * 수정 권한은 작성자가 아니라 "그 공지를 관리할 수 있는 사람"(전체: 관리자 / 분반: 운영진 이상) - 공지는 운영진 팀이 함께 관리하는 글이다.
 * 수정 시각 열은 없다 (Question 과 같은 규칙). 예약 게시·숨김은 범위 밖.
 *
 * 인덱스 명시 이유: PostgreSQL 은 FK 인덱스를 자동 생성하지 않는다. 목록은 "필독 먼저 → 최신순" 이라 (cohort_id, created_at) 이 분반별 조회를 받는다.
 */
@Entity
@Table(name = "notices", indexes = {
        @Index(name = "idx_notices_cohort_created", columnList = "cohort_id, created_at"),
        @Index(name = "idx_notices_author", columnList = "author_id")
})
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null 이면 전체 공지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 필독 - 목록 최상단 고정 */
    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Notice() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Notice(Cohort cohort, User author, String title, String content, boolean pinned) {
        this.cohort = cohort;
        this.author = author;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.createdAt = Instant.now();
    }

    /** 전체 공지 - 관리자 전용 (권한은 컨트롤러 @AdminOnly 가 보장) */
    public static Notice global(User author, String title, String content, boolean pinned) {
        return new Notice(null, author, title, content, pinned);
    }

    /** 분반 공지 - 그 분반 운영진 이상 */
    public static Notice forCohort(Cohort cohort, User author, String title, String content, boolean pinned) {
        return new Notice(cohort, author, title, content, pinned);
    }

    /** PUT 전체 교체 - 제목·내용·필독을 한 번에 바꾼다. 작성자·대상 분반은 바뀌지 않는다 */
    public void update(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    public boolean isGlobal() {
        return cohort == null;
    }

    public Long getId() { return id; }
    public Cohort getCohort() { return cohort; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
}
