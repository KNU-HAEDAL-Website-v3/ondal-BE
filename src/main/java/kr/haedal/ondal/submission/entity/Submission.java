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
import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;

/**
 * 제출 이력 - 백준처럼 재제출마다 행이 쌓인다(append-only). 수정·삭제 API 없음.
 * 상태(미제출/제출/제출(추가)/지각)는 저장하지 않고 이력과 dueAt으로 계산한다 (SubmissionStatus).
 *
 * user를 Enrollment이 아니라 직접 참조하는 이유: 소속이 해제돼도 제출물은 남는다 (docs/db/schema.md).
 * 본문(코드/파일 택1)·링크 최소 1개 규칙은 서비스가 강제한다 - DB CHECK는 Flyway 전환 시 추가.
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

    /** 본문·코드 - 붙여넣은 코드 텍스트. 파일과 택1 */
    @Column(columnDefinition = "text")
    private String codeText;

    /** 본문·코드 - 제출 언어(예: Python 3). 코드 제출일 때만. 표시·하이라이팅용, 채점과 무관 */
    @Column(length = 30)
    private String language;

    /** 본문·파일 - 업로드한 zip의 원본 파일명 (다운로드 시 복원) */
    @Column(length = 255)
    private String fileName;

    /** 본문·파일 - 저장 키 submissions/{UUID}.zip. P1은 로컬 디스크, S3 전환 시에도 이 열엔 키만 */
    @Column(length = 500)
    private String storedPath;

    /** 본문·파일 - 크기(byte) */
    private Long fileSize;

    /** 링크 - GitHub·배포 URL 등 (선택) */
    @Column(length = 2048)
    private String linkUrl;

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

    private Submission(Assignment assignment, User user, String codeText, String language,
                       String fileName, String storedPath, Long fileSize, String linkUrl, Instant submittedAt) {
        this.assignment = assignment;
        this.user = user;
        this.codeText = codeText;
        this.language = language;
        this.fileName = fileName;
        this.storedPath = storedPath;
        this.fileSize = fileSize;
        this.linkUrl = linkUrl;
        this.submittedAt = submittedAt;
    }

    /** 제출 시각 = 서버 수신 시각(마감 판정 기준 - 클라이언트 시계 불신) */
    public static Submission create(Assignment assignment, User user, String codeText, String language,
                                    String fileName, String storedPath, Long fileSize, String linkUrl) {
        return new Submission(assignment, user, codeText, language, fileName, storedPath, fileSize, linkUrl, Instant.now());
    }

    /** 제출 시각을 지정하는 버전 - 시더가 과거 제출을 재현할 때만. 서비스 코드에서 호출 금지 */
    public static Submission createAt(Assignment assignment, User user, String codeText, String language,
                                      String linkUrl, Instant submittedAt) {
        return new Submission(assignment, user, codeText, language, null, null, null, linkUrl, submittedAt);
    }

    public boolean hasFile() {
        return storedPath != null;
    }

    public Long getId() { return id; }
    public Assignment getAssignment() { return assignment; }
    public User getUser() { return user; }
    public String getCodeText() { return codeText; }
    public String getLanguage() { return language; }
    public String getFileName() { return fileName; }
    public String getStoredPath() { return storedPath; }
    public Long getFileSize() { return fileSize; }
    public String getLinkUrl() { return linkUrl; }
    public Instant getSubmittedAt() { return submittedAt; }
}
