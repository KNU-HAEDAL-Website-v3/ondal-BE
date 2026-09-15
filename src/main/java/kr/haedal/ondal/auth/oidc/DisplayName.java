package kr.haedal.ondal.auth.oidc;

/**
 * ID 토큰 클레임으로 표시 이름 만들기.
 *
 * Keycloak 의 `name` 클레임은 `given_name + " " + family_name` 으로 조립된다 - 서양식 순서다.
 * 한국 사용자가 이름(given)에 "철수", 성(family)에 "김" 을 넣으면 "철수 김" 으로 뒤집혀 들어온다.
 * 한글 이름은 성 + 이름 순서이고 사이를 띄우지 않으므로, 두 조각 중 하나라도 한글이면 여기서 다시 붙인다.
 *
 * 그 밖의 경우(영문 이름, 성·이름 중 한쪽만 있는 경우)는 홈페이지가 준 `name` 을 그대로 쓴다 - 신원의 원본은 홈페이지다.
 * 홈페이지 쪽 입력 방식이 바뀌어도(성에 전체 이름을 넣는 등) 이 규칙은 그대로 동작한다.
 */
public final class DisplayName {

    private DisplayName() {
    }

    /**
     * @param name       `name` 클레임 - 없으면 null
     * @param givenName  `given_name` 클레임 (이름)
     * @param familyName `family_name` 클레임 (성)
     * @return 표시 이름, 만들 수 없으면 null (호출부가 loginId 로 대체한다)
     */
    public static String of(String name, String givenName, String familyName) {
        String given = blankToNull(givenName);
        String family = blankToNull(familyName);
        if (given != null && family != null && (isHangul(given) || isHangul(family))) {
            return family + given;
        }
        return blankToNull(name);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 한 글자라도 한글이면 한국식 이름으로 본다 - 음절(가~힣)·자모 모두 HANGUL 스크립트 */
    private static boolean isHangul(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL);
    }
}
