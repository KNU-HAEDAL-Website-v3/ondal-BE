package kr.haedal.hoj.enrollment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.haedal.hoj.cohort.Cohort;
import kr.haedal.hoj.user.User;

import java.time.Instant;

/**
 * 소속: "이 사람은 이 분반에서 무엇인가". 한 사람은 한 분반에서 역할 하나(unique cohort+user).
 *
 * 하드 삭제되는 관계 테이블이며 다른 엔티티의 FK 대상이 아니다 —
 * 제출(Submission)은 (assignment_id, user_id)로 사용자를 직접 참조하고,
 * 대시보드의 행은 현재 Enrollment(STUDENT)에서 만든다. (docs: cohort/design.md §1)
 */
@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_cohort_user", columnNames = {"cohort_id", "user_id"}))
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Enrollment() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Enrollment(Cohort cohort, User user, EnrollmentRole role) {
        this.cohort = cohort;
        this.user = user;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public static Enrollment create(Cohort cohort, User user, EnrollmentRole role) {
        return new Enrollment(cohort, user, role);
    }

    /** STUDENT → OPERATOR 승격. 유일한 역할 변경 경로(ADMIN 전용 API에서만 호출). 강등은 없다. */
    public void promoteToOperator() {
        this.role = EnrollmentRole.OPERATOR;
    }

    public boolean isOperator() {
        return role == EnrollmentRole.OPERATOR;
    }

    public Long getId() { return id; }
    public Cohort getCohort() { return cohort; }
    public User getUser() { return user; }
    public EnrollmentRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
