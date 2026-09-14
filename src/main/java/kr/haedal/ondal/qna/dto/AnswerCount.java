package kr.haedal.ondal.qna.dto;

/** 질문별 답변 수 - AnswerRepository 집계 프로젝션 (JPQL select new) */
public record AnswerCount(Long questionId, long count) {
}
