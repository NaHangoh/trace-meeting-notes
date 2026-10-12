package tracenotes.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** SPEC F2: OpenAI 호환 /v1/chat/completions 호출. 실제 LLM 대신 JDK 가짜 HTTP 서버로 요청 본문과 오류 처리를 확인한다. */
@ExtendWith(OutputCaptureExtension.class)
class OpenAiCompatibleLlmClientTest {

    /** 테스트용 가짜 키. 실제 키가 아니다. */
    private static final String FAKE_KEY = "fake-test-key-7c1d";

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("items", Map.of("type", "array")),
            "required", List.of("items"));

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    record Captured(String path, String authorization, String body) {
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** 요청을 기록하고 status·body로 답하는 가짜 서버. delayMillis만큼 늦게 답한다. */
    private URI startServer(int status, String responseBody, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Captured(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), body));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content) + "}}]}";
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private OpenAiCompatibleLlmClient client(URI baseUrl, Duration timeout, boolean disableThinking) {
        LlmProperties properties = new LlmProperties(LlmProvider.OPENAI_COMPATIBLE, baseUrl, "test-model",
                FAKE_KEY, timeout, Duration.ofSeconds(10), disableThinking, null, null);
        return new OpenAiCompatibleLlmClient(properties, json);
    }

    private static LlmRequest request() {
        return new LlmRequest("시스템 지시", "사용자 내용", "summary", SCHEMA);
    }

    @Test
    void returnsMessageContent() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 0);

        String content = client(baseUrl, Duration.ofSeconds(5), false).complete(request());

        assertThat(content).isEqualTo("{\"items\":[]}");
        assertThat(requests).singleElement().extracting(Captured::path).isEqualTo("/v1/chat/completions");
    }

    /** SPEC F2: response_format(JSON 스키마)을 넣고, 도구 호출(tools, functions)은 넣지 않는다. */
    @Test
    void requestHasResponseFormatAndNoTools() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 0);

        client(baseUrl, Duration.ofSeconds(5), false).complete(request());

        JsonNode body = json.readTree(requests.get(0).body());
        assertThat(body.path("model").asString()).isEqualTo("test-model");
        assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(body.path("response_format").path("json_schema").path("name").asString()).isEqualTo("summary");
        assertThat(body.path("response_format").path("json_schema").path("schema").path("required").get(0).asString())
                .isEqualTo("items");
        assertThat(body.has("tools")).isFalse();
        assertThat(body.has("functions")).isFalse();
        assertThat(body.has("tool_choice")).isFalse();
        assertThat(body.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(body.path("messages").get(0).path("content").asString()).isEqualTo("시스템 지시");
        assertThat(body.path("messages").get(1).path("role").asString()).isEqualTo("user");
        assertThat(body.path("messages").get(1).path("content").asString()).isEqualTo("사용자 내용");
    }

    /** 샘플링은 설정값(app.llm.temperature, app.llm.seed)으로 고정해 요청마다 보낸다 (ADR 0002). */
    @Test
    void requestHasTemperatureAndSeedFromSettings() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 0);
        LlmProperties properties = new LlmProperties(LlmProvider.OPENAI_COMPATIBLE, baseUrl, "test-model",
                FAKE_KEY, Duration.ofSeconds(5), Duration.ofSeconds(10), false, 0.35, 7L);

        new OpenAiCompatibleLlmClient(properties, json).complete(request());

        JsonNode body = json.readTree(requests.get(0).body());
        assertThat(body.path("temperature").isNumber()).isTrue();
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.35);
        assertThat(body.path("seed").isIntegralNumber()).isTrue();
        assertThat(body.path("seed").asLong()).isEqualTo(7L);
    }

    @Test
    void requestHasChatTemplateKwargsOnlyWhenThinkingDisabled() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 0);

        client(baseUrl, Duration.ofSeconds(5), true).complete(request());
        client(baseUrl, Duration.ofSeconds(5), false).complete(request());

        JsonNode disabled = json.readTree(requests.get(0).body());
        JsonNode enabled = json.readTree(requests.get(1).body());
        assertThat(disabled.path("chat_template_kwargs").path("enable_thinking").isBoolean()).isTrue();
        assertThat(disabled.path("chat_template_kwargs").path("enable_thinking").asBoolean()).isFalse();
        assertThat(enabled.has("chat_template_kwargs")).isFalse();
    }

    /** 키는 Authorization 헤더로만 보낸다. 요청 본문에는 없다. */
    @Test
    void apiKeyOnlyInAuthorizationHeader() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 0);

        client(baseUrl, Duration.ofSeconds(5), false).complete(request());

        assertThat(requests.get(0).authorization()).isEqualTo("Bearer " + FAKE_KEY);
        assertThat(requests.get(0).body()).doesNotContain(FAKE_KEY);
    }

    /** 실행기가 오류 본문에 키나 요청 내용을 되돌려줘도 예외 메시지와 로그에는 들어가지 않는다. */
    @Test
    void apiKeyNotInErrorsOrLogs(CapturedOutput output) throws Exception {
        URI baseUrl = startServer(401, "{\"error\":\"bad key " + FAKE_KEY + " 사용자 내용\"}", 0);

        assertThatThrownBy(() -> client(baseUrl, Duration.ofSeconds(5), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmFailureKind.HTTP_ERROR);
                    assertThat(e.getMessage()).contains("401").doesNotContain(FAKE_KEY).doesNotContain("사용자 내용");
                });
        assertThat(output.getAll()).doesNotContain(FAKE_KEY).doesNotContain("사용자 내용");
    }

    @Test
    void timeoutIsTimeoutFailure() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 1_000);

        assertThatThrownBy(() -> client(baseUrl, Duration.ofMillis(200), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.kind()).isEqualTo(LlmFailureKind.TIMEOUT));
    }

    /** 서버 종료로 작업자 스레드가 중단되면 타임아웃이 아니라 중단으로 본다 (SPEC F2: SERVER_RESTART). 중단 표시는 유지한다. */
    @Test
    void interruptedCallIsInterruptedFailure() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 5000);
        OpenAiCompatibleLlmClient client = client(baseUrl, Duration.ofSeconds(30), false);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean stillInterrupted = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                client.complete(request());
            } catch (Throwable e) {
                thrown.set(e);
                stillInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        while (requests.isEmpty()) {
            Thread.onSpinWait();
        }

        caller.interrupt();
        caller.join(5000);

        assertThat(thrown.get()).isInstanceOfSatisfying(LlmCallException.class,
                e -> assertThat(e.kind()).isEqualTo(LlmFailureKind.INTERRUPTED));
        assertThat(stillInterrupted).isTrue();
    }

    @Test
    void connectionRefusedIsConnectionFailure() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        URI baseUrl = URI.create("http://127.0.0.1:" + closedPort + "/v1");

        assertThatThrownBy(() -> client(baseUrl, Duration.ofSeconds(5), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.kind()).isEqualTo(LlmFailureKind.CONNECTION_REFUSED));
    }

    /** 생각 모드가 꺼졌는지 확인할 수 있게 reasoning_content가 있었는지만 알려준다 (내용은 버림). */
    @Test
    void reportsWhetherReasoningContentWasPresent() throws Exception {
        URI withReasoning = startServer(200,
                "{\"choices\":[{\"message\":{\"content\":\"{}\",\"reasoning_content\":\"생각한 내용\"}}]}", 0);
        OpenAiCompatibleLlmClient.Completion thinking = client(withReasoning, Duration.ofSeconds(5), false)
                .completeWithReasoningFlag(request());
        server.stop(0);

        URI withoutReasoning = startServer(200, completion("{}"), 0);
        OpenAiCompatibleLlmClient.Completion plain = client(withoutReasoning, Duration.ofSeconds(5), true)
                .completeWithReasoningFlag(request());

        assertThat(thinking.reasoningPresent()).isTrue();
        assertThat(thinking.toString()).doesNotContain("생각한 내용");
        assertThat(plain.reasoningPresent()).isFalse();
        assertThat(plain.content()).isEqualTo("{}");
    }

    @Test
    void baseUrlTrailingSlashIsIgnored() throws Exception {
        URI baseUrl = startServer(200, completion("{\"items\":[]}"), 0);

        client(URI.create(baseUrl + "/"), Duration.ofSeconds(5), false).complete(request());

        assertThat(requests.get(0).path()).isEqualTo("/v1/chat/completions");
    }

    @Test
    void serverErrorIsHttpError() throws Exception {
        URI baseUrl = startServer(503, "{\"error\":\"loading model\"}", 0);

        assertThatThrownBy(() -> client(baseUrl, Duration.ofSeconds(5), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmFailureKind.HTTP_ERROR);
                    assertThat(e.getMessage()).contains("503").doesNotContain("loading model");
                });
    }

    /** 생각 모드에서 내용이 reasoning_content에만 있고 content가 null이면 응답 형식 오류다. */
    @Test
    void nullContentIsMalformedResponse() throws Exception {
        URI baseUrl = startServer(200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"reasoning_content\":\"생각\"}}]}", 0);

        assertThatThrownBy(() -> client(baseUrl, Duration.ofSeconds(5), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.kind()).isEqualTo(LlmFailureKind.MALFORMED_RESPONSE));
    }

    /** 응답 도중 연결이 끊기면(실행기 비정상 종료 등) 연결 실패로 본다 (다시 부르지 않음). */
    @Test
    void truncatedResponseIsConnectionFailure() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write("{\"ch".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.getHttpContext().getServer().stop(0);
        });
        server.start();
        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

        assertThatThrownBy(() -> client(baseUrl, Duration.ofSeconds(5), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class,
                        e -> assertThat(e.kind()).isEqualTo(LlmFailureKind.CONNECTION_REFUSED));
    }

    /** 연결 타임아웃(app.llm.connect-timeout)은 연결하지 못한 것이므로 호출 타임아웃이 아니라 연결 실패다. */
    @Test
    void connectTimeoutIsConnectionFailure() {
        assertThat(OpenAiCompatibleLlmClient.classify(new java.net.http.HttpConnectTimeoutException("connect")))
                .isEqualTo(LlmFailureKind.CONNECTION_REFUSED);
        assertThat(OpenAiCompatibleLlmClient.classify(new java.net.http.HttpTimeoutException("request")))
                .isEqualTo(LlmFailureKind.TIMEOUT);
        assertThat(OpenAiCompatibleLlmClient.classify(new java.net.ConnectException("refused")))
                .isEqualTo(LlmFailureKind.CONNECTION_REFUSED);
    }

    /** 응답이 OpenAI 형식이 아니면(choices 없음) 응답 형식 오류다. 응답 내용은 메시지에 넣지 않는다. */
    @Test
    void responseWithoutChoicesIsMalformedResponse() throws Exception {
        URI baseUrl = startServer(200, "{\"unexpected\":\"사용자 내용\"}", 0);

        assertThatThrownBy(() -> client(baseUrl, Duration.ofSeconds(5), false).complete(request()))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.kind()).isEqualTo(LlmFailureKind.MALFORMED_RESPONSE);
                    assertThat(e.getMessage()).doesNotContain("사용자 내용");
                });
    }
}
