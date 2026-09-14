package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.entity.JudgeResult;
import kr.haedal.ondal.judge.entity.JudgeStatus;
import kr.haedal.ondal.judge.entity.Verdict;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;

import java.time.Instant;
import java.util.List;

/** 내 제출 이력의 행 (#19) - 코드 전문(codeText)은 뺀다. 이력 N건에 코드 전문을 다 실으면 응답이 무거워짐 - 코드 확인은 #20 단건 */
public record SubmissionSummary(
        Long id,

        @Schema(description = "제출 형태 - CODE / FILE / LINK")
        SubmissionType type,

        String language,
        String fileName,
        Long fileSize,

        @Schema(description = "링크 URL 목록 - position 순. LINK 외 형태는 빈 배열")
        List<String> links,

        Instant submittedAt,

        @Schema(description = "지각 여부 - 서버 판정값. 프론트 재계산 금지")
        boolean late,
        @Schema(description = "운영진 코멘트가 달렸는가 - 목록 행의 '코멘트' 표시용. 내용은 단건(#20)에서")
        boolean hasComment,
        @Schema(description = "채점 상태 - 채점 대상이 아니면 null") JudgeStatus judgeStatus,
        @Schema(description = "판정 - DONE·ERROR 일 때만, 아니면 null. 표의 채점 결과 열") Verdict verdict
) {
    public static SubmissionSummary of(Submission submission, Instant dueAt, JudgeResult judge) {
        return new SubmissionSummary(
                submission.getId(),
                submission.getType(),
                submission.getLanguage(),
                submission.getFileName(),
                submission.getFileSize(),
                submission.getLinkUrls(),
                submission.getSubmittedAt(),
                submission.getSubmittedAt().isAfter(dueAt),
                submission.hasComment(),
                judge == null ? null : judge.getStatus(),
                judge == null ? null : judge.getVerdict()
        );
    }
}
