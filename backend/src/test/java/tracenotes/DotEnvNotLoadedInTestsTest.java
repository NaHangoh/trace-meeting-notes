package tracenotes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * 실행 시에는 루트 .env를 설정으로 읽지만(spring.config.import), 테스트 프로필에서는 읽지 않는다.
 * 테스트가 실제 비밀값에 닿지 않게 한다 (rules/testing.md).
 */
@SpringBootTest
class DotEnvNotLoadedInTestsTest {

    @Autowired
    ConfigurableEnvironment environment;

    @Test
    void testProfileDoesNotImportDotEnv() {
        assertThat(environment.getActiveProfiles()).contains("test");
        for (PropertySource<?> source : environment.getPropertySources()) {
            assertThat(source.getName()).doesNotContain(".env");
        }
    }
}
