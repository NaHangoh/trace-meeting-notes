package tracenotes.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 정리 설정값 (app.summary.*, SPEC F2).
 *
 * @param promptVersion 쓸 정리 프롬프트 버전 (prompts/summarize.{버전}.md). 기본 v2
 * @param chunkSize 덩어리 최대 글자 수(요청 줄 `[번호] 화자: 내용` 기준). 기본 6,000. eval로 조정한다
 * @param workers 동시에 실행하는 정리 작업 수. 기본 1 (ADR 0007)
 * @param queueCapacity 기다리는(QUEUED) 작업 상한. 실행 중인 작업은 세지 않는다. 기본 10 (ADR 0007)
 */
@ConfigurationProperties("app.summary")
public record SummaryProperties(String promptVersion, Integer chunkSize, Integer workers, Integer queueCapacity) {

    public static final String DEFAULT_PROMPT_VERSION = "v2";
    public static final int DEFAULT_CHUNK_SIZE = 6000;
    public static final int DEFAULT_WORKERS = 1;
    public static final int DEFAULT_QUEUE_CAPACITY = 10;

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
        if (workers == null) {
            workers = DEFAULT_WORKERS;
        }
        if (workers < 1) {
            throw new IllegalArgumentException("app.summary.workers must be at least 1");
        }
        if (queueCapacity == null) {
            queueCapacity = DEFAULT_QUEUE_CAPACITY;
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("app.summary.queue-capacity must be at least 1");
        }
    }
}
