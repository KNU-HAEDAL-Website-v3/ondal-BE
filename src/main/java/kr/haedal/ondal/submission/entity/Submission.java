package kr.haedal.ondal.submission.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 제출 이력 - 재제출마다 행이 쌓인다(append-only). 수정·삭제 API 없음.
 * 형태는 3종 택1(type: CODE/FILE/LINK) - 형태별 필수 필드는 서비스가 강제한다 (docs/db/schema.md 결정 8).
 * 상태(미제출/제출/제출(추가)/지각)는 저장하지 않고 이력과 dueAt으로 계산한다 (SubmissionStatus).
 *
 * user를 Enrollment이 아니라 직접 참조하는 이유: 소속이 해제돼도 제출물은 남는다 (docs/db/schema.md).
 */
@Entity
@Table(name = "submissions", indexes = {
        @Index(name = "idx_submissions_assignment_user", columnList = "assignment_id, user_id, submitted_at"),
        @Index(name = "idx_submissions_user", columnList = "user_id")
})
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 제출 형태 - 3종 택1. 응답 type 필드와 1:1 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubmissionType type;

    /** CODE 전용 - 붙여넣은 코드 텍스트 */
    @Column(columnDefinition = "text")
    private String codeText;

    /** CODE 전용·필수 - 제출 언어(예: Python 3). 하이라이팅 표시 + 채점(P2) 언어 식별 */
    @Column(length = 30)
    private String language;

    /** FILE 전용 - 업로드한 zip의 원본 파일명 (다운로드 시 복원) */
    @Column(length = 255)
    private String fileName;

    /** FILE 전용 - 저장 키 submissions/{UUID}.zip. P1은 로컬 디스크, S3 전환 시에도 이 열엔 키만 */
    @Column(length = 500)
    private String storedPath;

    /** FILE 전용 - 크기(byte). 한도 10MB */
    private Long fileSize;

    /** LINK 전용 - URL 1~5개, position 순 (제출과 함께 저장·삭제되는 자식) */
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<SubmissionLink> links = new ArrayList<>();

    /** 제출 시각(UTC) = 서버 수신 시각. dueAt과 비교해 지각을 계산한다 - 지각 플래그 열 없음 */
    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    /** [P2 준비] 채점 점수 - P1에서는 항상 null, API 미노출 (docs/db/schema.md) */
    @Column(name = "score")
    private Integer score;

    /** [P2 준비] 멘토 코멘트 - P1에서는 항상 null, API 미노출 */
    @Column(name = "mentor_comment", columnDefinition = "text")
    private String mentorComment;

    protected Submission() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Submission(Assignment assignment, User user, SubmissionType type, String codeText, String language,
                       String fileName, String storedPath, Long fileSize, Instant submittedAt) {
        this.assignment = assignment;
        this.user = user;
        this.type = type;
        this.codeText = codeText;
        this.language = language;
        this.fileName = fileName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
        this.submittedAt = submittedAt;
    }

    /** 제출 시각 = 서버 수신 시각(마감 판정 기준 - 클라이언트 시계 불신) */
    public static Submission create(Assignment assignment, User user, SubmissionType type, String codeText,
                                    String language, String fileName, String storedPath, Long fileSize,
                                    List<String> linkUrls) {
        return build(assignment, user, type, codeText, language, fileName, storedPath, fileSize, linkUrls, Instant.now());
    }

    /** 제출 시각을 지정하는 버전 - 시더가 과거 제출을 재현할 때만. 서비스 코드에서 호출 금지 */
    public static Submission createAt(Assignment assignment, User user, SubmissionType type, String codeText,
                                      String language, List<String> linkUrls, Instant submittedAt) {
        return build(assignment, user, type, codeText, language, null, null, null, linkUrls, submittedAt);
    }

    private static Submission build(Assignment assignment, User user, SubmissionType type, String codeText,
                                    String language, String fileName, String storedPath, Long fileSize,
                                    List<String> linkUrls, Instant submittedAt) {
        Submission submission = new Submission(assignment, user, type, codeText, language, fileName, storedPath, fileSize, submittedAt);
        if (linkUrls != null) {
            for (int i = 0; i < linkUrls.size(); i++) {
                submission.links.add(SubmissionLink.of(submission, linkUrls.get(i), i + 1));
            }
        }
        return submission;
    }

    public boolean hasFile() {
        return storedPath != null;
    }

    /** position 순 URL 목록 - DTO 조립용. LINK 외 형태는 빈 배열 */
    public List<String> getLinkUrls() {
        return links.stream().map(SubmissionLink::getUrl).toList();
    }

    public Long getId() { return id; }
    public Assignment getAssignment() { return assignment; }
    public User getUser() { return user; }
    public SubmissionType getType() { return type; }
    public String getCodeText() { return codeText; }
    public String getLanguage() { return language; }
    public String getFileName() { return fileName; }
    public String getStoredPath() { return storedPath; }
    public Long getFileSize() { return fileSize; }
    public List<SubmissionLink> getLinks() { return links; }
    public Instant getSubmittedAt() { return submittedAt; }
}
