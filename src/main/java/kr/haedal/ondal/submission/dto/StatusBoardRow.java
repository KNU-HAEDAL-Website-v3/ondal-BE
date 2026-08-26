package kr.haedal.ondal.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        Instant lastSubmittedAt
) {
}
