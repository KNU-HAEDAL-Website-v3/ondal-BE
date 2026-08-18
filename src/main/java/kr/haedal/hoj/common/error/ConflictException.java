package kr.haedal.hoj.common.error;

/**
 * 현재 상태와 충돌하는 요청 (예: 이미 다른 역할로 소속된 사람을 배정). → 409 CONFLICT
 * 사용: throw new ConflictException("이미 운영진으로 소속된 사용자입니다.");
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
