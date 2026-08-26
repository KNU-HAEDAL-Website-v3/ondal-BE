package kr.haedal.ondal.assignment.entity;

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

import java.time.Instant;

/**
 * 과제 - 분반에 속하며 차시 번호(선택)로 묶인다.
 * 조회는 항상 (id, cohort_id) 스코프 - 다른 분반의 과제는 존재를 드러내지 않는다(404).
 * 제출 상태(미제출/제출/제출(추가)/지각)는 Submission 이력과 dueAt으로 계산한다 - 이 엔티티에 상태 열 없음.
 *
 * 인덱스를 직접 명시하는 이유: PostgreSQL은 MySQL과 달리 FK에 인덱스를 자동 생성하지 않는다.
 */
@Entity
@Table(name = "assignments",
        indexes = @Index(name = "idx_assignments_cohort", columnList = "cohort_id"))
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    /** 차시 번호(선택) - 운영진 자유 입력, 중복·건너뜀 허용. 차시 밖 과제는 null */
    @Column(name = "session_no")
    private Integer sessionNo;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Instant dueAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Assignment() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Assignment(Cohort cohort, Integer sessionNo, String title, String description, Instant dueAt) {
        this.cohort = cohort;
        this.sessionNo = sessionNo;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
        this.createdAt = Instant.now();
    }

    public static Assignment create(Cohort cohort, Integer sessionNo, String title, String description, Instant dueAt) {
        return new Assignment(cohort, sessionNo, title, description, dueAt);
    }

    /** PUT 전체 교체. 마감(dueAt)이 바뀌면 지각 판정도 새 마감 기준으로 다시 계산된다 (제출 슬라이스) */
    public void update(Integer sessionNo, String title, String description, Instant dueAt) {
        this.sessionNo = sessionNo;
        this.title = title;
        this.description = description;
        this.dueAt = dueAt;
    }

    public Long getId() { return id; }
    public Cohort getCohort() { return cohort; }
    public Integer getSessionNo() { return sessionNo; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCreatedAt() { return createdAt; }
}
