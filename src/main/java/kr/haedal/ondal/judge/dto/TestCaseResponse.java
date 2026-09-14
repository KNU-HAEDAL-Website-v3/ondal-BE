package kr.haedal.ondal.judge.dto;

import kr.haedal.ondal.judge.entity.TestCase;

/** #47 응답의 케이스 1행 - id 는 통째 교체마다 바뀔 수 있으니 FE 는 position 으로 다룬다 */
public record TestCaseResponse(Long id, int position, String input, String expectedOutput, boolean isPublic) {

    public static TestCaseResponse of(TestCase testCase) {
        return new TestCaseResponse(testCase.getId(), testCase.getPosition(), testCase.getInput(), testCase.getExpectedOutput(), testCase.isPublic());
    }
}
