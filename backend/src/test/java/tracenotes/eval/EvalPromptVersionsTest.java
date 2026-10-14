package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** EVAL_PROMPT_VERSIONS: 없으면 설정 버전, 있으면 쉼표로 나눈 v숫자 목록. */
class EvalPromptVersionsTest {

    @Test
    void promptVersionsDefaultToSetting() {
        assertThat(EvalPromptVersions.parse(null, "v2")).containsExactly("v2");
        assertThat(EvalPromptVersions.parse("  ", "v2")).containsExactly("v2");
    }

    @Test
    void promptVersionsFromEnvironment() {
        assertThat(EvalPromptVersions.parse("v2,v3", "v2")).containsExactly("v2", "v3");
        assertThat(EvalPromptVersions.parse(" v3 , v2 ", "v2")).containsExactly("v3", "v2");
    }

    /** 경로 조작·오타·중복은 측정 전에 실패한다. */
    @ParameterizedTest
    @ValueSource(strings = {"v2,,v3", "v2,v2", "V3", "3", "v2,../x", "v3.md", ","})
    void malformedPromptVersionsFail(String value) {
        assertThatThrownBy(() -> EvalPromptVersions.parse(value, "v2")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EVAL_PROMPT_VERSIONS");
    }
}
