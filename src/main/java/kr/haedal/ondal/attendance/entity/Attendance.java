package kr.haedal.ondal.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;

/**
 * 출석 기록 - 한 차시에서 한 수강생의 판정 (session, user 유일). 운영진이 표시하며 누가·언제 표시했는지 남긴다.
 * 기록이 없으면 "미확인" - 판정하지 않은 학생을 결석으로 오해하지 않게 상태 값으로 두지 않는다 (docs attendance/design.md 결정 2).
 * 표시 API 는 차시 단위 일괄 upsert - 같은 (session, user) 는 status·checkedAt·checkedBy 를 갱신한다 (결정 4).
 */
@Entity
@Table(name = "attendances",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendances_session_user", columnNames = {"session_id", "user_id"}),
        indexes = @Index(name = "idx_attendances_user", columnList = "user_id"))
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checked_by", nullable = false)
    private User checkedBy;

    protected Attendance() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Attendance(Session session, User user, AttendanceStatus status, User checkedBy) {
        this.session = session;
        this.user = user;
        this.status = status;
        this.checkedAt = Instant.now();
        this.checkedBy = checkedBy;
    }

    public static Attendance mark(Session session, User user, AttendanceStatus status, User checkedBy) {
        return new Attendance(session, user, status, checkedBy);
    }

    /** 다시 표시 - 판정과 표시자·시각을 갱신한다 (upsert 의 update 쪽) */
    public void remark(AttendanceStatus status, User checkedBy) {
        this.status = status;
        this.checkedAt = Instant.now();
        this.checkedBy = checkedBy;
    }

    public Long getId() { return id; }
    public Session getSession() { return session; }
    public User getUser() { return user; }
    public AttendanceStatus getStatus() { return status; }
    public Instant getCheckedAt() { return checkedAt; }
    public User getCheckedBy() { return checkedBy; }
}
