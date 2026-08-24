package kr.haedal.ondal.common.error;

/**
 * 보관(ARCHIVED)된 분반을 변경하려는 요청. → 409 COHORT_ARCHIVED
 * 권한(403)이 아니라 도메인 규칙이다 - 프론트는 이 코드를 받으면 홈으로 보내지 않고 안내만 한다.
 * (docs: cohort/design.md 1절, permissions.md 2절)
 */
public class CohortArchivedException extends RuntimeException {

    public CohortArchivedException() {
        super("보관된 분반은 변경할 수 없습니다. 보관을 해제한 뒤 다시 시도하세요.");
    }
}
