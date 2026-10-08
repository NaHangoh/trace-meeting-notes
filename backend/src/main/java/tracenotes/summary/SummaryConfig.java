package tracenotes.summary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.llm.LlmClient;

/** 정리 구성. 프롬프트는 시작할 때 읽어 두고, 파일이 없거나 설정이 틀리면 시작하지 않는다. */
@Configuration
public class SummaryConfig {

    @Bean
    SummaryPromptBuilder summaryPromptBuilder(SummaryProperties properties) {
        return new SummaryPromptBuilder(new PromptLoader().summarizeTemplate(properties.promptVersion()));
    }

    @Bean
    SummaryValidator summaryValidator(JsonMapper json) {
        return new SummaryValidator(json);
    }

    @Bean
    ScriptChunker scriptChunker(SummaryProperties properties) {
        return new ScriptChunker(properties.chunkSize());
    }

    @Bean
    SummarizeService summarizeService(LlmClient llm, SummaryPromptBuilder builder, SummaryValidator validator,
            ScriptChunker chunker) {
        return new SummarizeService(llm, builder, validator, chunker);
    }
}
