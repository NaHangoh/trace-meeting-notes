package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

/**
 * 설정 검증으로 시작에 실패할 때 실제 콘솔 출력(FailureAnalyzer 보고 포함)에 키가 나오지 않는다.
 * ApplicationContextRunner는 FailureAnalyzer를 돌리지 않으므로 SpringApplication으로 실제 시작 과정을 거친다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LlmStartupFailureOutputTest {

    /** 테스트용 가짜 키. 실제 키가 아니다. */
    private static final String FAKE_KEY = "fake-startup-key-3e9a";

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    static class Config {
    }

    /** 설정은 명령행 인자로 넘긴다. 명령행 인자는 OS 환경 변수보다 우선하므로 셸 변수에 영향을 받지 않는다. */
    private static void start(String... properties) {
        String[] args = java.util.Arrays.stream(properties).map(p -> "--" + p).toArray(String[]::new);
        new SpringApplicationBuilder(Config.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "spring.config.location=optional:classpath:/no-such-config/")
                .run(args);
    }

    @Test
    void controlCharKeyIsNotPrintedOnStartupFailure(CapturedOutput output) {
        String keyWithCr = FAKE_KEY + Character.toString(13);

        assertThatThrownBy(() -> start(
                "app.llm.provider=openai-compatible",
                "app.llm.base-url=http://127.0.0.1:8081/v1",
                "app.llm.model=test-model",
                "app.llm.api-key=" + keyWithCr)).isInstanceOf(Exception.class);

        // 실패 보고가 실제로 출력됐는지 먼저 확인한다
        assertThat(output.getAll()).contains("LLM_API_KEY");
        assertThat(output.getAll()).doesNotContain(FAKE_KEY);
    }

    @Test
    void keyIsNotPrintedWhenOtherSettingFails(CapturedOutput output) {
        assertThatThrownBy(() -> start(
                "app.llm.provider=openai-compatible",
                "app.llm.base-url=localhost:8081/v1",
                "app.llm.model=test-model",
                "app.llm.api-key=" + FAKE_KEY)).isInstanceOf(Exception.class);

        assertThat(output.getAll()).contains("app.llm.base-url");
        assertThat(output.getAll()).doesNotContain(FAKE_KEY);
    }
}
