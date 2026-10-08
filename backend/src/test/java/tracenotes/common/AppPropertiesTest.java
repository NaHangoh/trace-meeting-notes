package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @ValueSource(strings = {" * ", "null", "NULL", " null ", "null/", " NULL/ ", "null//", "", "   "})
    void corsWildcardNullOrBlankEntryFailsStartup(String origin) {
        runner.withPropertyValues("app.cors-allowed-origins[0]=http://localhost:5173",
                        "app.cors-allowed-origins[1]=" + origin)
                .run(context -> assertThat(context).hasFailed());
    }

    /** Spring CORS 설정은 항목을 다시 쉼표로 나누므로, 한 항목에 숨긴 출처가 검사를 피할 수 있다. */
    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173,null", "http://localhost:5173, null/", "http://a.example,http://b.example"})
    void entryContainingCommaFailsStartup(String origin) {
        runner.withPropertyValues("app.cors-allowed-origins[0]=http://localhost:5173",
                        "app.cors-allowed-origins[1]=" + origin)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nullEntryFailsWithMessageNamingTheKey() {
        assertThatThrownBy(() -> new AppProperties(50_000, 204_800, Arrays.asList("http://localhost:5173", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.cors-allowed-origins");
    }

    @Test
    void invalidEntryMessageDoesNotContainValue() {
        assertThatThrownBy(() -> new AppProperties(50_000, 204_800, List.of("https://*.secret-host.example")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.cors-allowed-origins")
                .message().doesNotContain("secret-host");
    }

    @Test
    void originsAreStoredStripped() {
        AppProperties properties = new AppProperties(50_000, 204_800, List.of(" http://localhost:5173 "));

        assertThat(properties.corsAllowedOrigins()).containsExactly("http://localhost:5173");
    }

    /** 설정이 없으면 빈 목록: 시작은 되고 어떤 출처도 허용하지 않는다(개발 중에는 Vite 프록시로 같은 출처). */
    @Test
    void missingOriginsStartWithEmptyList() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AppProperties.class).corsAllowedOrigins()).isEmpty();
        });
    }

    /** 범위: maxScriptChars 1~1,000,000, maxRequestBytes 1~10,485,760(10MB). 0·음수·초과는 시작 실패. */
    @ParameterizedTest
    @CsvSource({
            "0, 204800",
            "-1, 204800",
            "1000001, 10485760",
            "50000, 0",
            "50000, -1",
            "50000, 10485761"
    })
    void invalidLimitsFailStartup(int maxScriptChars, int maxRequestBytes) {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.max-script-chars=" + maxScriptChars,
                        "app.max-request-bytes=" + maxRequestBytes)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingLimitsFailStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .run(context -> assertThat(context).hasFailed());
    }

    /** 범위 끝값은 통과한다(1,000,000자 × 4 + 여유분 ≤ 10MB). */
    @Test
    void boundaryLimitsStartNormally() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.max-script-chars=1000000", "app.max-request-bytes=10485760")
                .run(context -> assertThat(context).hasNotFailed());
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.max-script-chars=1", "app.max-request-bytes=" + (4 + AppProperties.JSON_OVERHEAD_BYTES))
                .run(context -> assertThat(context).hasNotFailed());
    }

    /** 바이트 상한이 글자 수 상한을 4바이트 문자로 채운 JSON보다 작으면 시작 실패. 기본값은 통과. */
    @Test
    void limitsAreConsistent() {
        int minimum = 50_000 * 4 + AppProperties.JSON_OVERHEAD_BYTES;

        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.max-script-chars=50000", "app.max-request-bytes=" + (minimum - 1))
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("app.max-script-chars=50000", "app.max-request-bytes=" + minimum)
                .run(context -> assertThat(context).hasNotFailed());
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void limitMessageNamesKeyWithoutValue() {
        assertThatThrownBy(() -> new AppProperties(1_234_567, 204_800, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.max-script-chars")
                .message().doesNotContain("1234567");
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
