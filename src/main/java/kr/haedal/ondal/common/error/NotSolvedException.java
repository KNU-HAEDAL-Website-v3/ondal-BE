package kr.haedal.ondal.common.error;

/**
 * 아직 못 푼 문제의 다른 사람 풀이를 열람하려는 요청. → 403 NOT_SOLVED (docs hoj/api.md 5절·9절)
 * FORBIDDEN(권한 부족)과 코드를 나누는 이유: FE 가 403 FORBIDDEN 은 홈으로 보내지만, 이 경우는 "먼저 맞히면 볼 수 있어요" 안내를 그 자리에 띄운다.
 */
public class NotSolvedException extends RuntimeException {
}
