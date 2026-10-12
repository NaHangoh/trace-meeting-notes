package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class LlmPropertiesTest {

    private static final String FAKE_KEY = "fake-test-key-7c1d";

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "app.llm.provider=openai-compatible",
                    "app.llm.base-url=http://127.0.0.1:8081/v1",
                    "app.llm.model=test-model",
                    "app.llm.timeout=5m");

    /** OpenAI 호환 실행기를 쓰는데 키가 없으면 시작하지 않는다. 오류 메시지에는 변수 이름만 넣는다. */
    @Test
    void missingApiKeyFailsStartupWithKeyNameOnly() {
        runner.withPropertyValues("app.llm.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("LLM_API_KEY");
                });
    }

    /**
     * 키에 제어 문자(CRLF로 저장한 .env의 줄 끝 등)가 섞이면 HTTP 헤더를 만들 때 JDK 예외 메시지에 키가 그대로 들어간다.
     * 그래서 시작할 때 막고, 메시지에는 변수 이름만 넣는다.
     */
    @Test
    void apiKeyWithControlCharFailsStartupWithoutValue() {
        String keyWithCr = FAKE_KEY + Character.toString(13);

        assertThatThrownBy(() -> new LlmProperties(LlmProvider.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:8081/v1"), "test-model", keyWithCr, Duration.ofMinutes(5),
                Duration.ofSeconds(10), true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM_API_KEY")
                .message().doesNotContain(FAKE_KEY);
    }

    @Test
    void fixtureProviderNeedsNoApiKey() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.llm.provider=fixture")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /** 설정 객체를 로그에 찍어도 키가 나오지 않는다. */
    @Test
    void toStringHidesApiKey() {
        LlmProperties properties = new LlmProperties(LlmProvider.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:8081/v1"), "test-model", FAKE_KEY, Duration.ofMinutes(5),
                Duration.ofSeconds(10), true, null, null);

        assertThat(properties.toString()).doesNotContain(FAKE_KEY).contains("test-model");
    }

    /** 키는 있고 다른 설정이 틀려 시작에 실패해도, 실패 원인 사슬 어디에도 키 값이 나오지 않는다. */
    @Test
    void startupFailureChainHasNoKeyWhenOtherSettingInvalid() {
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY, "app.llm.model=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                        assertThat(String.valueOf(t.getMessage())).doesNotContain(FAKE_KEY);
                        assertThat(t.toString()).doesNotContain(FAKE_KEY);
                    }
                });
    }

    /** scheme이 http·https가 아닌 주소(예: localhost:8081/v1)는 호출 때가 아니라 시작할 때 막는다. */
    @Test
    void baseUrlWithoutHttpSchemeFailsStartup() {
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY, "app.llm.base-url=localhost:8081/v1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("app.llm.base-url");
                });
    }

    /** 연결 타임아웃은 호출 타임아웃과 따로 두고 기본 10초다. */
    @Test
    void defaultConnectTimeoutIsTenSeconds() {
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY)
                .run(context -> assertThat(context.getBean(LlmProperties.class).connectTimeout())
                        .isEqualTo(Duration.ofSeconds(10)));
    }

    /** 기본 타임아웃은 SPEC F2의 호출당 5분이다. */
    @Test
    void defaultTimeoutIsFiveMinutes() {
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY, "app.llm.timeout=")
                .run(context -> assertThat(context.getBean(LlmProperties.class).timeout())
                        .isEqualTo(Duration.ofMinutes(5)));
    }

    /** 정리 작업이라 샘플링은 낮은 temperature 0.2, 고정 seed 42가 기본이다 (ADR 0002). */
    @Test
    void defaultTemperatureAndSeed() {
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY)
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.temperature()).isEqualTo(0.2);
                    assertThat(properties.seed()).isEqualTo(42L);
                });
    }

    @Test
    void temperatureAndSeedFromSettings() {
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY, "app.llm.temperature=0", "app.llm.seed=0")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.temperature()).isEqualTo(0.0);
                    assertThat(properties.seed()).isEqualTo(0L);
                });
        runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY, "app.llm.temperature=2")
                .run(context -> assertThat(context.getBean(LlmProperties.class).temperature()).isEqualTo(2.0));
    }

    /** 실행 설정(application.yml)의 값도 ADR 0002의 기본값과 같다. */
    @Test
    void applicationYmlHasDefaultTemperatureAndSeed() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        // 첫 문서가 기본 설정이고, 다른 문서(프로필별)는 이 값을 덮어쓰지 않는다
        assertThat(String.valueOf(sources.get(0).getProperty("app.llm.temperature"))).isEqualTo("0.2");
        assertThat(String.valueOf(sources.get(0).getProperty("app.llm.seed"))).isEqualTo("42");
        assertThat(sources.subList(1, sources.size())).allSatisfy(source -> {
            assertThat(source.getProperty("app.llm.temperature")).isNull();
            assertThat(source.getProperty("app.llm.seed")).isNull();
        });
    }

    /** temperature는 0~2, seed는 0 이상만 받는다 (llama-server의 -1은 무작위 seed라 고정이 아님). */
    @Test
    void temperatureOrSeedOutOfRangeFailsStartup() {
        for (String setting : List.of("app.llm.temperature=-0.1", "app.llm.temperature=2.1", "app.llm.temperature=NaN",
                "app.llm.seed=-1")) {
            runner.withPropertyValues("app.llm.api-key=" + FAKE_KEY, setting)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(setting.substring(0, setting.indexOf('=')));
                    });
        }
    }
}
