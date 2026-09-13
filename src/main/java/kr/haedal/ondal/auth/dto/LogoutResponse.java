package kr.haedal.ondal.auth.dto;

/**
 * 로그아웃 응답. Ondal 세션은 이미 끝난 상태다.
 * logoutUrl: 홈페이지(Keycloak) 세션까지 끝내려면 FE 가 이 주소로 이동한다(브라우저 이동, fetch 아님) -
 *   끝나면 Keycloak 이 FE 로그인 화면으로 돌려보낸다. 공용 PC 에서 다음 사람이 앞사람 계정으로 SSO 자동 로그인되는 것을 막는 장치.
 *   스텁 모드이거나 홈페이지가 로그아웃 주소를 제공하지 않으면 null - FE 는 그냥 로그인 화면으로 가면 된다.
 */
public record LogoutResponse(String logoutUrl) {
}
