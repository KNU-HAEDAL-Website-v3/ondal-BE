package kr.haedal.ondal.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 문제 - 분반과 무관한 라이브러리 항목 (docs judge/design.md 결정 17).
 *
 * 전에는 "문제 = 과제" 였다. 같은 문제를 다시 내려면 과제를 새로 만들고 테스트케이스까지 복제해야 했고,
 * 복제본끼리 채점 기준이 갈라졌다. 이제 문제는 하나만 두고 배정(Assignment)이 그것을 가리킨다 - 재출제는 배정 한 줄.
 *
 * 문제가 가진 것: 번호·제목·본문·실행 제한·테스트케이스(TestCase)·태그.
 * 배정이 가진 것: 분반·차시·마감.
 */
@Entity
@Table(name = "problems", uniqueConstraints = @UniqueConstraint(name = "uk_problems_problem_no", columnNames = "problem_no"))
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문제 번호 - 전역 유일, 1000부터 (schema.md 결정 9). 채번·중복 검사는 서비스, unique 제약은 동시성 최후 방어 */
    @Column(name = "problem_no", nullable = false)
    private Integer problemNo;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** 자동 채점 제한(선택) - null 이면 서버 기본값(ondal.judge.default-*) */
    @Column(name = "time_limit_ms")
    private Integer timeLimitMs;

    @Column(name = "memory_limit_mb")
    private Integer memoryLimitMb;

    /** 출제자 - V7 로 이관된 기존 문제는 작성자를 알 수 없어 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 태그 - 추가 열이 없는 순수 연결이라 조인 테이블로 둔다.
     * 문제를 지울 때 연결도 함께 지우도록 서비스가 먼저 비운다(FK RESTRICT 유지).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "problem_tags",
            joinColumns = @JoinColumn(name = "problem_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected Problem() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Problem(Integer problemNo, String title, String description, Integer timeLimitMs, Integer memoryLimitMb, User createdBy) {
        this.problemNo = problemNo;
        this.title = title;
        this.description = description;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Problem create(Integer problemNo, String title, String description,
                                 Integer timeLimitMs, Integer memoryLimitMb, User createdBy) {
        return new Problem(problemNo, title, description, timeLimitMs, memoryLimitMb, createdBy);
    }

    /** PUT 전체 교체 - 번호는 따로(renumber), 테스트케이스는 채점 설정 API 로 바뀐다 */
    public void update(String title, String description) {
        this.title = title;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void renumber(Integer problemNo) {
        this.problemNo = problemNo;
        this.updatedAt = Instant.now();
    }

    /** 채점 설정 저장 - null 은 "기본값 사용" */
    public void updateJudgeLimits(Integer timeLimitMs, Integer memoryLimitMb) {
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.updatedAt = Instant.now();
    }

    /** 태그 통째 교체 - 화면이 고른 목록이 곧 결과다(부분 추가·삭제 API 를 두지 않는다) */
    public void replaceTags(Collection<Tag> replacement) {
        this.tags.clear();
        this.tags.addAll(replacement);
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Integer getProblemNo() { return problemNo; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getTimeLimitMs() { return timeLimitMs; }
    public Integer getMemoryLimitMb() { return memoryLimitMb; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Tag> getTags() { return tags; }
}
