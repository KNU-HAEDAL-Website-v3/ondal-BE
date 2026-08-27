package kr.haedal.ondal.submission.entity;

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

/**
 * LINK 제출의 URL 1~5개 (docs/db/schema.md 결정 8).
 * 제출과 함께 생성·삭제되는 자식 - Submission의 cascade로만 저장하며 단독 리포지토리가 없다.
 * 개수(1~5)·position 연속성은 서비스가 강제한다.
 */
@Entity
@Table(name = "submission_links", indexes = {
        @Index(name = "idx_submission_links_submission", columnList = "submission_id")
})
public class SubmissionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(nullable = false, length = 2048)
    private String url;

    /** 입력 순서 1~5 - 표시 순서 보존 */
    @Column(nullable = false)
    private Integer position;

    protected SubmissionLink() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private SubmissionLink(Submission submission, String url, Integer position) {
        this.submission = submission;
        this.url = url;
        this.position = position;
    }

    static SubmissionLink of(Submission submission, String url, int position) {
        return new SubmissionLink(submission, url, position);
    }

    public Long getId() { return id; }
    public Submission getSubmission() { return submission; }
    public String getUrl() { return url; }
    public Integer getPosition() { return position; }
}
