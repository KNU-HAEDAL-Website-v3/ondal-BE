package kr.haedal.ondal.judge.dto;

import kr.haedal.ondal.judge.entity.Verdict;

/**
 * 케이스 1개의 채점 결과 - judge_results.case_results(jsonb) 에 배열로 저장되는 모양.
 * actualOutput 은 4KB 로 잘라 저장(truncated). 공개 여부·입력·기대 출력은 저장하지 않고 응답 조립 때 test_cases 에서 position 으로 찾는다.
 */
public record JudgeCaseResult(int position, Verdict verdict, Integer timeMs, Integer memoryKb, String actualOutput, boolean truncated) {
}
