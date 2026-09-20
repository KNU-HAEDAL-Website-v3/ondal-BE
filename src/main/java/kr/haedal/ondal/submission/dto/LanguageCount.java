package kr.haedal.ondal.submission.dto;

/** 언어별 제출 수 (SubmissionRepository 조회 결과) - 사용자 페이지의 언어 비율. 응답에 그대로 실린다 */
public record LanguageCount(String language, Long count) {
}
