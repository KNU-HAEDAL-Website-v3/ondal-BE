package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.dto.JudgeResultResponse;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;
import java.util.List;

/** 제출 단건 응답 (#18 생성·#20 상세) - 코드 전문 포함. 이력 목록은 SubmissionSummary */
public record SubmissionResponse(
        Long id,

        @Schema(description = "제출자 - 최소 정보(id·이름·직책)만")
        UserSummary user,

        @Schema(description = "제출 형태 - CODE / FILE / LINK")
        SubmissionType type,

        String codeText,
        String language,
        String fileName,
        Long fileSize,

        @Schema(description = "링크 URL 목록 - position 순. LINK 외 형태는 빈 배열")
        List<String> links,

        @Schema(description = "제출 시각(UTC) = 서버 수신 시각 - KST 변환은 프론트 몫")
        Instant submittedAt,

        @Schema(description = "지각 여부 - 서버 판정값(submittedAt > dueAt). 프론트 재계산 금지. 마감이 수정되면 재조회 시 값이 바뀔 수 있다")
        boolean late,
        @Schema(description = "운영진 코멘트 - 없으면 null (2026-09-14 도입, 점수 없음)")
        SubmissionComment comment,
        @Schema(description = "자동 채점 결과 - CODE 제출이고 자동 채점 문제일 때만, 아니면 null. 제출 직후는 status PENDING (judge/api.md 2절)")
        JudgeResultResponse judge
) {
    public static SubmissionResponse of(Submission submission, Instant dueAt, UserSummary user, SubmissionComment comment, JudgeResultResponse judge) {
        return new SubmissionResponse(
                submission.getId(),
                user,
                submission.getType(),
                submission.getCodeText(),
                submission.getLanguage(),
                submission.getFileName(),
                submission.getFileSize(),
                submission.getLinkUrls(),
                submission.getSubmittedAt(),
                submission.getSubmittedAt().isAfter(dueAt),
                comment,
                judge
        );
    }

    /**
     * HOJ 연습 제출 - 마감이 없으니 지각도 없고(late=false), 운영진 코멘트도 달 수 없다 (V7).
     * dueAt 을 받는 of(...) 에 가짜 마감을 넘기는 대신 팩토리를 따로 둔다 - "연습에는 마감이 없다"가 코드에 드러나야 한다.
     */
    public static SubmissionResponse practice(Submission submission, UserSummary user, JudgeResultResponse judge) {
        return new SubmissionResponse(
                submission.getId(),
                user,
                submission.getType(),
                submission.getCodeText(),
                submission.getLanguage(),
                null,
                null,
                List.of(),
                submission.getSubmittedAt(),
                false,
                null,
                judge
        );
    }
}
