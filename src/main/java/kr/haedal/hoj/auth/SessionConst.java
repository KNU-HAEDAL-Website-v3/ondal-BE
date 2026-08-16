package kr.haedal.hoj.auth;

public final class SessionConst {

    /**
     * 세션에는 User 엔티티가 아니라 id만 넣는다.
     * 엔티티를 통째로 넣으면 이후 DB에서 이름/역할이 바뀌어도 세션 속 낡은 사본을 계속 믿게 된다.
     * id만 저장하고 요청마다 DB에서 최신 상태를 읽는다(LoginUserArgumentResolver).
     */
    public static final String LOGIN_USER_ID = "LOGIN_USER_ID";

    private SessionConst() {
    }
}
