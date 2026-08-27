package kr.haedal.ondal.submission.entity;

/**
 * 제출 형태 - 3종 택1 (docs/db/schema.md 결정 8).
 * 형태별 필수 필드(CODE=codeText·language, FILE=zip, LINK=링크 1~5개)는 서비스가 검증한다.
 */
public enum SubmissionType {
    CODE, FILE, LINK
}
