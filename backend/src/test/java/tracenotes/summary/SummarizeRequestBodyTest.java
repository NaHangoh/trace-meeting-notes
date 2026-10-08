package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.llm.LlmProperties;
import tracenotes.llm.LlmProvider;
import tracenotes.llm.OpenAiCompatibleLlmClient;
import tracenotes.minutes.Utterance;

/**
 * SPEC F2 수용 기준: 실제 v2 프롬프트 → 빌더 → OpenAI 호환 클라이언트를 거친 요청 본문을 가짜 HTTP 서버로 확인한다.
 * response_format 있음, tools·functions·tool_choice 없음, 발언은 구분자 블록 안에만, 덩어리마다 그 덩어리의 발언만.
 */
class SummarizeRequestBodyTest {

    private static final String FAKE_KEY = "fake-test-key-7c1d";

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private URI startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{\\\"items\\\":[]}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    @Test
    void requestBodyHasResponseFormatNoToolsAndUtterancesOnlyInBlock() throws Exception {
        URI baseUrl = startServer();
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new LlmProperties(
                LlmProvider.OPENAI_COMPATIBLE, baseUrl, "test-model", FAKE_KEY, Duration.ofSeconds(5),
                Duration.ofSeconds(5), true), json);
        SummarizeService service = new SummarizeService(client,
                new SummaryPromptBuilder(new PromptLoader().summarizeTemplate("v2")),
                new SummaryValidator(json), new ScriptChunker(100));
        // 줄 하나 30자, 100자 덩어리 → 3줄씩 덩어리 2개
        List<Utterance> utterances = IntStream.rangeClosed(1, 6)
                .mapToObj(n -> new Utterance(n, "참석자", "발언내용" + n + "가".repeat(16)))
                .toList();

        service.summarize(utterances);

        assertThat(bodies).hasSize(2);
        List<List<Integer>> perRequest = new ArrayList<>();
        for (String raw : bodies) {
            List<Integer> seen = new ArrayList<>();
            perRequest.add(seen);
            JsonNode body = json.readTree(raw);
            assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_schema");
            assertThat(body.path("response_format").path("json_schema").path("name").asString()).isEqualTo("summary");
            assertThat(body.has("tools")).isFalse();
            assertThat(body.has("functions")).isFalse();
            assertThat(body.has("tool_choice")).isFalse();

            String system = body.path("messages").get(0).path("content").asString();
            String user = body.path("messages").get(1).path("content").asString();
            assertThat(body.path("messages").size()).isEqualTo(2);
            assertThat(system).doesNotContain("발언내용");

            List<String> lines = user.lines().toList();
            String delimiter = lines.get(0).substring(3);
            assertThat(lines.get(0)).isEqualTo("<<<" + delimiter);
            assertThat(lines.get(lines.size() - 1)).isEqualTo(delimiter + ">>>");
            assertThat(system).contains("<<<" + delimiter).contains(delimiter + ">>>");
            for (String line : lines.subList(1, lines.size() - 1)) {
                assertThat(line).matches("\\[\\d+\\] 참석자: 발언내용\\d+가+");
                seen.add(Integer.parseInt(line.substring(1, line.indexOf(']'))));
            }
        }
        // 덩어리마다 그 덩어리의 발언만 순서대로 들어가고, 모든 발언이 한 번씩 들어간다
        assertThat(perRequest).containsExactly(List.of(1, 2, 3), List.of(4, 5, 6));
    }
}
