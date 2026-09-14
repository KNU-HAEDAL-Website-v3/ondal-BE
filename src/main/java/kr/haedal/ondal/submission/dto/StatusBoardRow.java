package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.judge.entity.JudgeStatus;
import kr.haedal.ondal.judge.entity.Verdict;
import kr.haedal.ondal.submission.entity.SubmissionStatus;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/**
 * 현황판(#22)의 행 - 행 목록 = 현재 STUDENT 소속 명단(이름순). 미제출자도 행으로 나온다.
 * 소속 해제된 학생은 명단에서 빠진다 - 제출 데이터는 남지만 현황판은 현재 명단 기준 (docs/db/schema.md 3절).
 */
public record StatusBoardRow(
        UserSummary user,

        @Schema(description = "상태 - NOT_SUBMITTED(미제출) / SUBMITTED(제출) / SUBMITTED_EXTRA(제출 후 추가 제출) / LATE(지각)")
        SubmissionStatus status,

        @Schema(description = "제출 이력 총 건수")
        int submissionCount,

        @Schema(description = "최근 제출 시각(UTC) - 제출 없으면 null")
        Instant lastSubmittedAt,

        @Schema(description = "최신 제출 id - 제출물 상세(#20)·파일 다운로드(#21) 진입용. 제출 없으면 null (최신 제출 = 대표)")
        Long latestSubmissionId,

        @Schema(description = "최신 제출에 운영진 코멘트가 달렸는가 - 운영진이 아직 검토하지 않은 제출을 한눈에. 제출 없으면 false")
        boolean latestCommented,

        @Schema(description = "최신 제출의 채점 상태 - 채점 대상이 아니거나 제출 없음이면 null")
        JudgeStatus latestJudgeStatus,

        @Schema(description = "최신 제출의 판정 - DONE·ERROR 일 때만. 현황판 판정 열")
        Verdict latestVerdict
) {
}
