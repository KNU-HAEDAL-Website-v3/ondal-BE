package kr.haedal.ondal.problem.dto;

/** 태그별 문제 수 (ProblemRepository 조회 결과) - 사용자 페이지 태그 숙련도의 분모. 문제가 하나도 없는 태그는 행이 없다 */
public record TagProblemCount(Long tagId, String name, Long count) {
}
