package kr.haedal.ondal.attendance.entity;

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
import jakarta.persistence.UniqueConstraint;
import kr.haedal.ondal.cohort.entity.Cohort;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 차시(수업 회차) - 분반에 속하고 번호·날짜·제목을 갖는다. 출석 기록(Attendance)의 단위.
 * 과제(Assignment.sessionNo)와는 같은 번호 체계로 느슨히 대응할 뿐 FK 가 없다 - P1 과제 슬라이스를 흔들지 않기 위함 (docs attendance/design.md 결정 1).
 * 번호는 분반 안에서 유일(uk_sessions_cohort_no) - 등록 시 비우면 서비스가 최대 + 1 로 채번한다.
 * heldOn 은 KST 달력일(LocalDate) - 다른 시각 열(UTC Instant)과 다른 타입을 쓰는 유일한 곳 (결정 8).
 */
@Entity
@Table(name = "sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sessions_cohort_no", columnNames = {"cohort_id", "session_no"}),
        indexes = @Index(name = "idx_sessions_cohort_held", columnList = "cohort_id, held_on"))
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "session_no", nullable = false)
    private Integer sessionNo;

    @Column(length = 100)
    private String title;

    @Column(name = "held_on", nullable = false)
    private LocalDate heldOn;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Session() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Session(Cohort cohort, Integer sessionNo, String title, LocalDate heldOn) {
        this.cohort = cohort;
        this.sessionNo = sessionNo;
        this.title = title;
        this.heldOn = heldOn;
        this.createdAt = Instant.now();
    }

    public static Session create(Cohort cohort, Integer sessionNo, String title, LocalDate heldOn) {
        return new Session(cohort, sessionNo, title, heldOn);
    }

    /** PUT 전체 교체 - 번호·제목·날짜. 분반은 바뀌지 않는다 */
    public void update(Integer sessionNo, String title, LocalDate heldOn) {
        this.sessionNo = sessionNo;
        this.title = title;
        this.heldOn = heldOn;
    }

    public Long getId() { return id; }
    public Cohort getCohort() { return cohort; }
    public Integer getSessionNo() { return sessionNo; }
    public String getTitle() { return title; }
    public LocalDate getHeldOn() { return heldOn; }
    public Instant getCreatedAt() { return createdAt; }
}
