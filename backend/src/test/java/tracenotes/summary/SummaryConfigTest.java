package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.llm.FixtureLlmClient;
import tracenotes.llm.LlmClient;
import tracenotes.minutes.Utterance;

/** 쓸 프롬프트 버전과 덩어리 크기는 설정값이고(app.summary.*), 틀리면 시작하지 않는다. */
class SummaryConfigTest {

    @Configuration
    @EnableConfigurationProperties(SummaryProperties.class)
    @Import(SummaryConfig.class)
    static class Config {

        @Bean
        LlmClient llmClient() {
            return new FixtureLlmClient();
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void promptVersionFromConfig() {
        runner.withPropertyValues("app.summary.prompt-version=v2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SummaryPromptBuilder.class)).isNotNull();
                });
    }

    @Test
    void defaultPromptVersionIsV2() {
        runner.run(context -> assertThat(context.getBean(SummaryProperties.class).promptVersion()).isEqualTo("v2"));
    }

    @Test
    void missingPromptFileFailsStartup() {
        runner.withPropertyValues("app.summary.prompt-version=v999")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("summarize.v999.md");
                });
    }

    /** SPEC F2: 덩어리 최대 6,000자(설정값). */
    @Test
    void defaultChunkSizeIs6000() {
        runner.run(context -> assertThat(context.getBean(SummaryProperties.class).chunkSize()).isEqualTo(6000));
    }

    @Test
    void chunkSizeBelowMinimumFailsStartup() {
        runner.withPropertyValues("app.summary.chunk-size=99")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("app.summary.chunk-size");
                });
    }

    /** 설정한 덩어리 크기가 ScriptChunker까지 전달된다: 줄 하나 30자(+줄바꿈)는 100자에 3줄, 200자에 6줄. */
    @Test
    void chunkSizeFromConfigReachesChunker() {
        List<Utterance> utterances = IntStream.rangeClosed(1, 6)
                .mapToObj(n -> new Utterance(n, "참석자", "가".repeat(21)))
                .toList();
        runner.withPropertyValues("app.summary.chunk-size=100")
                .run(context -> assertThat(context.getBean(ScriptChunker.class).chunk(utterances)).hasSize(2));
        runner.withPropertyValues("app.summary.chunk-size=200")
                .run(context -> assertThat(context.getBean(ScriptChunker.class).chunk(utterances)).hasSize(1));
    }

    @Test
    void summarizeServiceIsWired() {
        runner.run(context -> assertThat(context.getBean(SummarizeService.class)).isNotNull());
    }
}
