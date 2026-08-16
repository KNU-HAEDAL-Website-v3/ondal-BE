package kr.haedal.hoj.common.error;

/**
 * 모든 에러 응답의 공통 모양.
 * code는 프론트가 분기하는 기계용 문자열(예: UNAUTHENTICATED → 재로그인 유도 + 작성 내용 보존),
 * message는 사람에게 보여줄 한국어 문장.
 */
public record ErrorResponse(String code, String message) {
}
