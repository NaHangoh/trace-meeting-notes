package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withInitializer(new ConfigDataApplicationContextInitializer());

    /** SPEC S4: 작업 생성 기본 20회, 개발 프로필 200회. application.yml과 application-dev.yml을 읽어 확인한다. */
    @Test
    void defaultAndDevProfileJobLimits() {
        runner.run(context -> assertThat(context.getBean(RateLimitProperties.class).jobPerHour()).isEqualTo(20));
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context.getBean(RateLimitProperties.class).jobPerHour()).isEqualTo(200));
    }

    @Test
    void nonPositiveLimitsFailStartup() {
        runner.withPropertyValues("app.rate-limit.job-per-hour=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rate-limit.preview-per-minute=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
