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
import kr.haedal.ondal.problem.entity.Problem;

import java.time.Instant;

/**
 * 과제 = "이 문제를 이 분반에 이 마감으로 배정한 것" (docs judge/design.md 결정 17, V7).
 *
 * 문제 자체(번호·제목·본문·실행 제한·테스트케이스)는 Problem 이 갖는다 - 같은 문제를 여러 분반·여러 학기에 배정해도
 * 문제는 하나를 공유하므로 테스트케이스가 갈라지지 않는다. 재출제 = 이 행을 하나 더 만드는 것.
 *
 * 조회는 항상 (id, cohort_id) 스코프 - 다른 분반의 과제는 존재를 드러내지 않는다(404).
 * 제출 상태(미제출/제출/제출(추가)/지각)는 Submission 이력과 dueAt으로 계산한다 - 이 엔티티에 상태 열 없음.
 *
 * 인덱스를 직접 명시하는 이유: PostgreSQL은 MySQL과 달리 FK에 인덱스를 자동 생성하지 않는다.
 */
@Entity
@Table(name = "assignments", indexes = {
        @Index(name = "idx_assignments_cohort", columnList = "cohort_id"),
        @Index(name = "idx_assignments_problem", columnList = "problem_id")
})
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    /** 배정한 문제 - 제목·본문·번호·채점 설정의 출처 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    /** 차시 번호(선택) - 운영진 자유 입력, 중복·건너뜀 허용. 차시 밖 과제는 null */
    @Column(name = "session_no")
    private Integer sessionNo;

    @Column(nullable = false)
    private Instant dueAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Assignment() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Assignment(Cohort cohort, Problem problem, Integer sessionNo, Instant dueAt) {
        this.cohort = cohort;
        this.problem = problem;
        this.sessionNo = sessionNo;
        this.dueAt = dueAt;
        this.createdAt = Instant.now();
    }

    public static Assignment create(Cohort cohort, Problem problem, Integer sessionNo, Instant dueAt) {
        return new Assignment(cohort, problem, sessionNo, dueAt);
    }

    /**
     * PUT 전체 교체. 마감(dueAt)이 바뀌면 지각 판정도 새 마감 기준으로 다시 계산된다 (제출 슬라이스).
     * 배정된 문제를 다른 문제로 바꾸는 것도 허용한다 - 잘못 고른 문제를 배정 삭제 없이 고칠 수 있어야 한다.
     */
    public void update(Problem problem, Integer sessionNo, Instant dueAt) {
        this.problem = problem;
        this.sessionNo = sessionNo;
        this.dueAt = dueAt;
    }

    public Long getId() { return id; }
    public Cohort getCohort() { return cohort; }
    public Problem getProblem() { return problem; }
    public Integer getSessionNo() { return sessionNo; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCreatedAt() { return createdAt; }
}
