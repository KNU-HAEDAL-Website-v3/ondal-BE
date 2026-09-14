package kr.haedal.ondal.qna.entity;

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
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;

/**
 * Q&A 답변 - 질문 글 하나에 여러 개, 작성자 한 명. 분반 소속 누구나 쓸 수 있다(운영진 전용이 아님 - 동료 답변 장려).
 * 조회는 항상 (id, question_id) 스코프 - 다른 질문의 답변은 존재를 드러내지 않는다(404). 질문이 삭제되면 서비스가 함께 지운다.
 * 채택·좋아요는 없다 (docs qna/design.md 결정 11) - 필요해지면 열 추가.
 */
@Entity
@Table(name = "answers", indexes = {
        @Index(name = "idx_answers_question_created", columnList = "question_id, created_at"),
        @Index(name = "idx_answers_author", columnList = "author_id")
})
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Answer() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Answer(Question question, User author, String content) {
        this.question = question;
        this.author = author;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public static Answer create(Question question, User author, String content) {
        return new Answer(question, author, content);
    }

    /** PUT 전체 교체 - 내용만. 작성자·질문은 바뀌지 않는다 */
    public void update(String content) {
        this.content = content;
    }

    /** 작성자 본인인가 - 수정 권한 판정용. 프록시의 getId()는 DB를 치지 않는다 */
    public boolean isWrittenBy(User user) {
        return author.getId().equals(user.getId());
    }

    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
