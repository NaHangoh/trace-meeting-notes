package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 쓸 프롬프트 버전은 설정값 app.summary.prompt-version이고, 파일이 없으면 시작하지 않는다. */
class SummaryConfigTest {

    @Configuration
    @EnableConfigurationProperties(SummaryProperties.class)
    @Import(SummaryConfig.class)
    static class Config {
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
}
