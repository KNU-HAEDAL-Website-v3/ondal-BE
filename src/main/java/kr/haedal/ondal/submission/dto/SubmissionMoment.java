package kr.haedal.ondal.submission.dto;

import java.time.Instant;

/**
 * 상태 계산용 최소 조회(프로젝션) - 제출 시각만 필요할 때 codeText(TEXT) 전문을 끌어오지 않기 위한 것.
 * 과제 응답 조립(assignmentId 기준 그룹핑)과 현황판(userId 기준 그룹핑)이 공용한다.
 */
public record SubmissionMoment(Long assignmentId, Long userId, Instant submittedAt) {
}
