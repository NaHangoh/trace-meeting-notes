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

    /** v3도 같은 형식이라 요청을 만들 수 있다 (구분자 자리가 있고 변경 기록 주석은 빠진다). */
    @Test
    void v3TemplateLoadsWithPlaceholder() {
        String template = new PromptLoader().summarizeTemplate("v3");

        assertThat(template).contains("{{DELIMITER}}");
        assertThat(template).doesNotContain("<!--").doesNotContain("-->");
        assertThat(template).isNotEqualTo(new PromptLoader().summarizeTemplate("v2"));
        String system = new SummaryPromptBuilder(template).build(
                java.util.List.of(new tracenotes.minutes.Utterance(1, "김민수", "안건입니다."))).systemPrompt();
        assertThat(system).doesNotContain("{{DELIMITER}}").contains("SCRIPT_");
    }

    /** v4는 v2에서 출발해 줄만 더했다: v2의 줄이 모두 순서대로 남아 있고, 더한 줄은 두 개다. */
    @Test
    void v4KeepsEveryV2LineAndAddsTwo() {
        String v2 = new PromptLoader().summarizeTemplate("v2");
        String v4 = new PromptLoader().summarizeTemplate("v4");

        assertThat(v4).contains("{{DELIMITER}}").doesNotContain("<!--").doesNotContain("-->");
        java.util.List<String> v2Lines = v2.lines().toList();
        java.util.List<String> v4Lines = v4.lines().toList();
        assertThat(v4Lines).containsSubsequence(v2Lines);
        assertThat(v4Lines).hasSize(v2Lines.size() + 2);
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
