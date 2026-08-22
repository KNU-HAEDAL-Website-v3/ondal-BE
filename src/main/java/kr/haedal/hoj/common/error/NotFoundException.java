package kr.haedal.hoj.common.error;

/**
 * 요청한 자원이 없거나, 경로의 부모(분반)에 속하지 않는 경우. → 404 NOT_FOUND
 * 남의 분반 자원을 찔러본 경우도 403이 아니라 404 - 존재 여부를 노출하지 않는다.
 * 사용: throw new NotFoundException("분반을 찾을 수 없습니다.");
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
