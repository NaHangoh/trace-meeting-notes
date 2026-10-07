package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.max-script-chars=50000", "app.max-request-bytes=204800");

    @ParameterizedTest
    @ValueSource(strings = {"*", "http://localhost:5173,*", "https://*.example.com"})
    void corsWildcardFailsStartup(String origins) {
        runner.withPropertyValues("app.cors-allowed-origins=" + origins)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitOriginsStartNormally() {
        runner.withPropertyValues("app.cors-allowed-origins=http://localhost:5173")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppProperties.class).corsAllowedOrigins())
                            .containsExactly("http://localhost:5173");
                });
    }
}
