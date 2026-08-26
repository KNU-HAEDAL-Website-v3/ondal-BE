package kr.haedal.ondal.submission.entity;

import java.time.Instant;
import java.util.Collection;

/**
 * 한 학생의 한 과제 제출 상태 - 엔티티 열이 아니라 계산 결과 (docs/db/schema.md 3절).
 * 마감(dueAt)이 수정되면 같은 이력에서 다른 상태가 나온다 - 마감 연장으로 지각이 제출로 바뀌는 것은 의도된 동작.
 * FE는 이 값을 그대로 배지에 매핑한다 - 프론트 재계산 금지.
 */
public enum SubmissionStatus {

    /** 제출 이력 없음 */
    NOT_SUBMITTED,
    /** 마감 내 제출만 있음 (초록) */
    SUBMITTED,
    /** 마감 내 제출 + 마감 후 재제출도 있음 (초록 계열) */
    SUBMITTED_EXTRA,
    /** 마감 후 제출만 있음 (주황) */
    LATE;

    /** 판정의 단일 출처 - 과제 응답 조립과 현황판이 모두 이걸 쓴다. onTime = submittedAt <= dueAt */
    public static SubmissionStatus from(Collection<Instant> submittedAts, Instant dueAt) {
        boolean onTime = submittedAts.stream().anyMatch(at -> !at.isAfter(dueAt));
        boolean late = submittedAts.stream().anyMatch(at -> at.isAfter(dueAt));
        if (onTime && late) {
            return SUBMITTED_EXTRA;
        }
        if (onTime) {
            return SUBMITTED;
        }
        if (late) {
            return LATE;
        }
        return NOT_SUBMITTED;
    }
}
