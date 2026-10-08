package tracenotes.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** 사용할 LLM 구현은 설정 app.llm.provider로만 바꾼다 (rules/llm.md). */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    LlmClient llmClient(LlmProperties properties, JsonMapper json) {
        return switch (properties.provider()) {
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleLlmClient(properties, json);
            case FIXTURE -> {
                // fixture는 정해 둔 응답을 돌려주므로 정리가 실제로 된 것처럼 보일 수 있다
                log.warn("fixture 모드: 정리 결과는 실제 LLM 결과가 아님");
                yield new FixtureLlmClient();
            }
        };
    }
}
