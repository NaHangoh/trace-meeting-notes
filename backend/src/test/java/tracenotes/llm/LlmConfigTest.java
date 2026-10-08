package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class LlmConfigTest {

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    @Import(LlmConfig.class)
    static class Config {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    /** fixture로 시작하면 정리 결과가 실제 LLM 결과가 아니라는 WARN 로그를 남긴다. */
    @Test
    void fixtureModeLogsWarning(CapturedOutput output) {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.llm.provider=fixture")
                .run(context -> {
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(FixtureLlmClient.class);
                    assertThat(output.getAll()).contains("WARN").contains("fixture 모드: 정리 결과는 실제 LLM 결과가 아님");
                });
    }
}
