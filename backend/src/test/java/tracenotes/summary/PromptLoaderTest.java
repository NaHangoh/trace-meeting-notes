package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 제품 프롬프트는 prompts/의 파일로 관리하고, 쓸 버전은 설정값이다 (rules/llm.md). */
class PromptLoaderTest {

    /** 빌드 때 prompts/가 classpath의 prompts/로 복사된다. 변경 기록 주석은 LLM에 보내지 않는다. */
    @Test
    void loadsSummarizeV2WithoutChangeNoteComment() {
        String template = new PromptLoader().summarizeTemplate("v2");

        assertThat(template).contains("{{DELIMITER}}");
        assertThat(template).doesNotContain("<!--").doesNotContain("-->");
        assertThat(template).isEqualTo(template.strip());
    }

    @Test
    void unknownVersionFailsWithVersionOnly() {
        assertThatThrownBy(() -> new PromptLoader().summarizeTemplate("v999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summarize.v999.md");
    }

    /** 버전은 v숫자만 받는다. 경로 조작(../ 등)으로 다른 파일을 읽지 못한다. */
    @ParameterizedTest
    @ValueSource(strings = {"../application", "v2/../../x", "V2", "2", "v", "", "v2.md"})
    void versionMustBeVNumber(String version) {
        assertThatThrownBy(() -> new PromptLoader().summarizeTemplate(version))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.summary.prompt-version");
    }
}
