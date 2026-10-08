package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 자동 테스트는 셸 환경 변수(APP_LLM_PROVIDER, LLM_API_KEY 등)가 있어도 FixtureLlmClient만 쓴다 (rules/llm.md).
 * OS 환경 변수는 application-test.yml보다 우선하므로 Gradle test 작업에서 시스템 속성으로 고정하고 변수를 지운다.
 */
@SpringBootTest
class TestsUseFixtureLlmTest {

    @Autowired
    LlmClient llmClient;

    @Test
    void automatedTestsAlwaysUseFixtureLlm() {
        assertThat(llmClient).isInstanceOf(FixtureLlmClient.class);
    }

    /** 키 값은 보지 않고, 테스트 JVM에 키 변수가 넘어오지 않았는지만 확인한다. */
    @Test
    void llmSecretsAreNotPassedToTestJvm() {
        assertThat(System.getenv().containsKey("LLM_API_KEY")).isFalse();
        assertThat(System.getenv().containsKey("APP_LLM_PROVIDER")).isFalse();
    }
}
