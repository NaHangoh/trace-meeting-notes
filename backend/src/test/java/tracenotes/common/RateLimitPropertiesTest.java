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

    /** SPEC F2: 작업 조회 기본 분당 120회. 미리보기·작업 생성과 다른 설정값이다. */
    @Test
    void defaultStatusLimitIsSeparateSetting() {
        runner.run(context -> {
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);
            assertThat(properties.statusPerMinute()).isEqualTo(120);
            assertThat(properties.previewPerMinute()).isEqualTo(60);
        });
        runner.withPropertyValues("app.rate-limit.status-per-minute=7")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);
                    assertThat(properties.statusPerMinute()).isEqualTo(7);
                    assertThat(properties.previewPerMinute()).isEqualTo(60);
                    assertThat(properties.jobPerHour()).isEqualTo(20);
                });
    }

    @Test
    void nonPositiveLimitsFailStartup() {
        runner.withPropertyValues("app.rate-limit.status-per-minute=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rate-limit.job-per-hour=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rate-limit.preview-per-minute=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
