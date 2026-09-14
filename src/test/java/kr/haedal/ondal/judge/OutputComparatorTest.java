package kr.haedal.ondal.judge;

import kr.haedal.ondal.judge.service.OutputComparator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 비교 규칙 고정 (docs judge/design.md 결정 3) - 줄 끝 공백·\r\n·마지막 빈 줄은 무시, 그 외는 정확 일치 */
class OutputComparatorTest {

    @Test
    void 줄_끝_공백과_마지막_빈_줄과_CRLF_는_무시한다() {
        assertThat(OutputComparator.matches("3\n", "3")).isTrue();
        assertThat(OutputComparator.matches("3", "3\n\n\n")).isTrue();
        assertThat(OutputComparator.matches("1 2\n3 4\n", "1 2   \n3 4\t\n")).isTrue();
        assertThat(OutputComparator.matches("a\nb\n", "a\r\nb\r\n")).isTrue();
        assertThat(OutputComparator.matches("a\n\n  \n", "a")).isTrue();
        assertThat(OutputComparator.matches("", "")).isTrue();
        assertThat(OutputComparator.matches("", "\n\n")).isTrue();
        assertThat(OutputComparator.matches(null, "")).isTrue();
    }

    @Test
    void 줄_중간_공백_줄_순서_대소문자_중간_빈_줄은_그대로_비교한다() {
        assertThat(OutputComparator.matches("1 2", "1  2")).isFalse();
        assertThat(OutputComparator.matches("a\nb", "b\na")).isFalse();
        assertThat(OutputComparator.matches("Yes", "yes")).isFalse();
        assertThat(OutputComparator.matches("a\nb", "a\n\nb")).isFalse();
        assertThat(OutputComparator.matches(" 3", "3")).isFalse();
        assertThat(OutputComparator.matches("3", "")).isFalse();
    }
}
