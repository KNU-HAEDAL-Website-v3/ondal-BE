package kr.haedal.ondal.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 북마크 - 사용자가 문제에 붙이는 표시 (V11, docs 결정 13). PK = (user_id, problem_id), 멱등 PUT/DELETE.
 *
 * User·Problem 을 연관으로 잇지 않고 id 만 갖는 이유: 읽는 곳이 "내 북마크 문제 id 묶음"(목록·상세의 bookmarked) 하나뿐이라
 * 지연 로딩이 끼어들 자리가 없다 (JudgeResult 와 같은 선택). 문제 삭제 시 서비스가 함께 지운다.
 */
@Entity
@IdClass(ProblemBookmark.Key.class)
@Table(name = "problem_bookmarks", indexes = @Index(name = "idx_problem_bookmarks_problem", columnList = "problem_id"))
public class ProblemBookmark {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "problem_id")
    private Long problemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProblemBookmark() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private ProblemBookmark(Long userId, Long problemId) {
        this.userId = userId;
        this.problemId = problemId;
        this.createdAt = Instant.now();
    }

    public static ProblemBookmark of(Long userId, Long problemId) {
        return new ProblemBookmark(userId, problemId);
    }

    public Long getUserId() { return userId; }
    public Long getProblemId() { return problemId; }
    public Instant getCreatedAt() { return createdAt; }

    /** 복합 키 - 필드 이름이 엔티티의 @Id 필드와 같아야 한다 (@IdClass 규약) */
    public static class Key implements Serializable {
        private Long userId;
        private Long problemId;

        public Key() {
        }

        public Key(Long userId, Long problemId) {
            this.userId = userId;
            this.problemId = problemId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(userId, other.userId) && Objects.equals(problemId, other.problemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, problemId);
        }
    }
}
