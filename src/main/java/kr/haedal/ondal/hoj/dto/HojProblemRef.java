package kr.haedal.ondal.hoj.dto;

import kr.haedal.ondal.problem.entity.Problem;

/** 피드·최근 제출 행에 실리는 문제 요약 - 화면이 "#번호 제목" 링크를 그리는 데 필요한 것만 */
public record HojProblemRef(Long id, Integer problemNo, String title) {

    public static HojProblemRef from(Problem problem) {
        return new HojProblemRef(problem.getId(), problem.getProblemNo(), problem.getTitle());
    }
}
