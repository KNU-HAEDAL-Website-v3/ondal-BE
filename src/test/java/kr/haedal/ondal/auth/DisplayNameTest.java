package kr.haedal.ondal.auth;

import kr.haedal.ondal.auth.oidc.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 표시 이름 조합 규칙 고정 - Keycloak 의 name 은 "이름 성"(given family) 순서라 한국 이름이 뒤집혀 온다.
 * 한글이 섞이면 성 + 이름 으로 붙이고, 그 외에는 name 을 그대로 쓴다.
 */
class DisplayNameTest {

    @Test
    void 한글_이름은_성과_이름을_붙여_성_이름_순서로_만든다() {
        assertThat(DisplayName.of("철수 김", "철수", "김")).isEqualTo("김철수");
        assertThat(DisplayName.of("길동 홍", "길동", "홍")).isEqualTo("홍길동");
        // 두 글자 성·외자 이름
        assertThat(DisplayName.of("민 남궁", "민", "남궁")).isEqualTo("남궁민");
        // 앞뒤 공백은 정리한다
        assertThat(DisplayName.of("  철수   김  ", " 철수 ", " 김 ")).isEqualTo("김철수");
        // 한쪽만 한글이어도 한국식으로 본다 (영문 이름을 쓰는 부원의 성만 한글인 경우)
        assertThat(DisplayName.of("Chulsoo 김", "Chulsoo", "김")).isEqualTo("김Chulsoo");
    }

    @Test
    void 영문_이름은_홈페이지가_준_name_을_그대로_쓴다() {
        assertThat(DisplayName.of("John Doe", "John", "Doe")).isEqualTo("John Doe");
    }

    @Test
    void 성과_이름_중_하나라도_없으면_name_을_그대로_쓴다() {
        // 홈페이지에서 이름 칸 하나에 전체 이름을 넣은 경우 - 이미 올바른 순서라 건드리지 않는다
        assertThat(DisplayName.of("김철수", "김철수", null)).isEqualTo("김철수");
        assertThat(DisplayName.of("김철수", null, "김철수")).isEqualTo("김철수");
        assertThat(DisplayName.of("김철수", "김철수", "  ")).isEqualTo("김철수");
    }

    @Test
    void 이름이_없으면_null_이라_호출부가_loginId_로_대체한다() {
        assertThat(DisplayName.of(null, null, null)).isNull();
        assertThat(DisplayName.of("  ", null, null)).isNull();
        assertThat(DisplayName.of(null, "철수", null)).isNull();
    }
}
