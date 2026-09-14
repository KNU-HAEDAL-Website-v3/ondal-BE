package kr.haedal.ondal.judge.entity;

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
import kr.haedal.ondal.assignment.entity.Assignment;

/**
 * 테스트케이스 - 과제(= 문제)의 입력과 기대 출력 1쌍 (docs judge/design.md 결정 1).
 * 과제에 1개 이상 있으면 자동 채점 문제. 통째 교체(PUT)로만 바뀌므로 수정 메서드가 없다 - 행을 지우고 다시 만든다.
 */
@Entity
@Table(name = "test_cases", indexes = @Index(name = "idx_test_cases_assignment", columnList = "assignment_id, position"))
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    /** 0부터, 실행 순서 */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "text")
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "text")
    private String expectedOutput;

    /** 학생에게 예시로 보이고, 채점 결과에서 실제 출력까지 노출되는 케이스 */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    protected TestCase() {
    }

    private TestCase(Assignment assignment, int position, String input, String expectedOutput, boolean isPublic) {
        this.assignment = assignment;
        this.position = position;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.isPublic = isPublic;
    }

    public static TestCase create(Assignment assignment, int position, String input, String expectedOutput, boolean isPublic) {
        return new TestCase(assignment, position, input, expectedOutput, isPublic);
    }

    public Long getId() { return id; }
    public Assignment getAssignment() { return assignment; }
    public int getPosition() { return position; }
    public String getInput() { return input; }
    public String getExpectedOutput() { return expectedOutput; }
    public boolean isPublic() { return isPublic; }
}
