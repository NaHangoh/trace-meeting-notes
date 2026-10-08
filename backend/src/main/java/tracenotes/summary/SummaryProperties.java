package tracenotes.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정리 설정값 (app.summary.*, SPEC F2).
 *
 * @param promptVersion 쓸 정리 프롬프트 버전 (prompts/summarize.{버전}.md). 기본 v2
 * @param chunkSize 덩어리 최대 글자 수(요청 줄 `[번호] 화자: 내용` 기준). 기본 6,000. eval로 조정한다
 */
@ConfigurationProperties("app.summary")
public record SummaryProperties(String promptVersion, Integer chunkSize) {

    public static final String DEFAULT_PROMPT_VERSION = "v2";
    public static final int DEFAULT_CHUNK_SIZE = 6000;

    public SummaryProperties {
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = DEFAULT_PROMPT_VERSION;
        }
        if (chunkSize == null) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }
        if (chunkSize < ScriptChunker.MIN_CHARS) {
            throw new IllegalArgumentException("app.summary.chunk-size must be at least " + ScriptChunker.MIN_CHARS);
        }
    }
}
