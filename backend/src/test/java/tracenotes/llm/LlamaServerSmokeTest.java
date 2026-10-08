package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 llama-server를 한 번 부른다 (@Tag("llm"), 기본 test 작업에서 제외, `gradlew llmTest`로만 실행).
 * 사용자가 실행기를 띄운 뒤 직접 실행한다. 설정과 키는 실행 설정(application.yml + 루트 .env)에서 Spring이 읽는다.
 * 확인: 생각 모드가 꺼지는지(<think> 없음), response_format 스키마대로 나오는지, 걸린 시간.
 * 응답 내용은 출력하지 않고 결과만 출력한다.
 */
@Tag("llm")
class LlamaServerSmokeTest {

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    @Import(LlmConfig.class)
    static class Config {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    private static final Set<String> TYPES = Set.of("AGENDA", "DISCUSSION", "DECISION", "TODO");

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("items"),
            "properties", Map.of("items", Map.of(
                    "type", "array",
                    "items", Map.of(
                            "type", "object",
                            "additionalProperties", false,
                            "required", List.of("type", "text", "evidence"),
                            "properties", Map.of(
                                    "type", Map.of("type", "string", "enum", List.copyOf(TYPES)),
                                    "text", Map.of("type", "string"),
                                    "evidence", Map.of("type", "array", "items", Map.of("type", "integer")))))));

    /** 가상 회의 (실제 회의·실명 아님). */
    private static final String SCRIPT = String.join("\n",
            "[1] 진행자: 오늘 안건은 다음 달 사내 교육 일정입니다.",
            "[2] 참석자 A: 둘째 주 화요일이 좋겠습니다.",
            "[3] 참석자 B: 저도 화요일 찬성입니다.",
            "[4] 진행자: 그럼 둘째 주 화요일로 정하겠습니다. 참석자 A가 장소 예약을 맡아 주세요.");

    @Test
    void llamaServerFollowsSchemaWithoutThinking() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LlmClient client = context.getBean(LlmClient.class);
                    JsonMapper json = context.getBean(JsonMapper.class);
                    LlmRequest request = new LlmRequest(
                            "회의 발언을 회의록 항목으로 정리하라. 항목마다 근거 발언 번호를 evidence에 넣어라. JSON만 출력하라.",
                            "<<<SCRIPT\n" + SCRIPT + "\nSCRIPT>>>", "summary", SCHEMA);

                    assertThat(client).isInstanceOf(OpenAiCompatibleLlmClient.class);
                    long started = System.nanoTime();
                    OpenAiCompatibleLlmClient.Completion completion =
                            ((OpenAiCompatibleLlmClient) client).completeWithReasoningFlag(request);
                    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                    String content = completion.content();

                    boolean thinkTagAbsent = !content.contains("<think>");
                    boolean reasoningEmpty = !completion.reasoningPresent();
                    int[] itemCount = {-1};
                    boolean schemaValid = isValid(json, content, itemCount);
                    // 응답 내용은 출력하지 않는다
                    System.out.println("LLM_SMOKE thinkTagAbsent=" + thinkTagAbsent + " reasoningEmpty=" + reasoningEmpty
                            + " schemaValid=" + schemaValid + " items=" + itemCount[0] + " elapsedMs=" + elapsedMs);

                    assertThat(thinkTagAbsent).as("thinkTagAbsent").isTrue();
                    assertThat(reasoningEmpty).as("reasoningEmpty").isTrue();
                    assertThat(schemaValid).as("schemaValid").isTrue();
                });
    }

    private static boolean isValid(JsonMapper json, String content, int[] itemCount) {
        JsonNode root;
        try {
            root = json.readTree(content);
        } catch (RuntimeException e) {
            return false;
        }
        if (!root.isObject() || root.size() != 1 || !root.path("items").isArray()) {
            return false;
        }
        itemCount[0] = root.path("items").size();
        for (JsonNode item : root.path("items")) {
            if (!item.isObject() || item.size() != 3
                    || !TYPES.contains(item.path("type").asString(""))
                    || !item.path("text").isString()
                    || !item.path("evidence").isArray()) {
                return false;
            }
            for (JsonNode number : item.path("evidence")) {
                if (!number.isIntegralNumber()) {
                    return false;
                }
            }
        }
        return true;
    }
}
