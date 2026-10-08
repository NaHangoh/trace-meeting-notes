package tracenotes.summary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 정리 프롬프트를 시작할 때 읽어 둔다. 파일이 없거나 버전 형식이 틀리면 시작하지 않는다. */
@Configuration
public class SummaryConfig {

    @Bean
    SummaryPromptBuilder summaryPromptBuilder(SummaryProperties properties) {
        return new SummaryPromptBuilder(new PromptLoader().summarizeTemplate(properties.promptVersion()));
    }
}
