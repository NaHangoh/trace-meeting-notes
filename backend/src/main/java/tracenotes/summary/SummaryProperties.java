package tracenotes.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정리 설정값 (app.summary.*, SPEC F2).
 *
 * @param promptVersion 쓸 정리 프롬프트 버전 (prompts/summarize.{버전}.md). 기본 v2
 */
@ConfigurationProperties("app.summary")
public record SummaryProperties(String promptVersion) {

    public static final String DEFAULT_PROMPT_VERSION = "v2";

    public SummaryProperties {
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = DEFAULT_PROMPT_VERSION;
        }
    }
}
