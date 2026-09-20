package kr.haedal.ondal.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;

/**
 * 정답 코드(참고 풀이) - 문제마다 언어별 1건 (V11, docs 결정 13).
 *
 * 운영진이 채점 설정·질문 답변 때 참고하는 용도라 운영진 이상만 읽고 쓴다. 학생에게는 존재 자체가 보이지 않는다 -
 * "다른 사람 풀이는 맞힌 뒤에만" 규칙과 충돌하기 때문. 문제 은행 레포의 solutions/sol.<ext> 가 가져오기 때 여기로 들어온다.
 * 저장은 언제나 통째 교체(PUT) 라 수정 메서드가 없다 - 바뀐 언어 묶음이 곧 결과다.
 */
@Entity
@Table(name = "problem_solutions",
        uniqueConstraints = @UniqueConstraint(name = "uk_problem_solutions_problem_language", columnNames = {"problem_id", "language"}))
public class ProblemSolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    /** 제출 언어와 같은 표기 (ondal.judge.languages 키) - 언어 탭 하나에 코드 하나 */
    @Column(nullable = false, length = 30)
    private String language;

    @Column(name = "code_text", nullable = false, columnDefinition = "text")
    private String codeText;

    /** 마지막으로 저장한 사람 - 화면의 "누가 언제" 표시용. 가져오기면 요청한 관리자 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProblemSolution() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private ProblemSolution(Problem problem, String language, String codeText, User updatedBy) {
        this.problem = problem;
        this.language = language;
        this.codeText = codeText;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public static ProblemSolution create(Problem problem, String language, String codeText, User updatedBy) {
        return new ProblemSolution(problem, language, codeText, updatedBy);
    }

    public Long getId() { return id; }
    public Problem getProblem() { return problem; }
    public String getLanguage() { return language; }
    public String getCodeText() { return codeText; }
    public User getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
