package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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
                Duration.ofSeconds(10), true))
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
                Duration.ofSeconds(10), true);

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
}
