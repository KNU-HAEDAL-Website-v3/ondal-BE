package kr.haedal.ondal.judge.service;

/**
 * 출력 비교 규칙 (docs judge/design.md 결정 3) - 판정은 엔진이 아니라 여기서 난다.
 *  - 줄바꿈 \r\n 을 \n 으로
 *  - 각 줄의 끝 공백(스페이스·탭) 제거
 *  - 마지막의 빈 줄(들) 제거
 *  - 그 뒤 완전 일치
 * 줄 중간 공백·줄 순서·대소문자는 그대로 비교한다 (백준 "일반" 채점과 같은 취지).
 */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder(value.length());
        for (String line : lines) {
            sb.append(line.stripTrailing()).append('\n');
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '\n') {
            end--;
        }
        return sb.substring(0, end);
    }
}
