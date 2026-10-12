package tracenotes.llm;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI 호환 /v1/chat/completions 호출 (llama-server, Ollama 공용, ADR 0002).
 * 요청에 도구 호출(tools, functions)을 넣지 않고, response_format(JSON 스키마)으로 출력 형식을 강제한다.
 * 키는 Authorization 헤더로만 보내고, 예외 메시지에는 실패 종류와 상태 코드만 넣는다. 아무것도 로그에 남기지 않는다.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final JsonMapper json;
    private final URI endpoint;
    private final HttpClient http;

    public OpenAiCompatibleLlmClient(LlmProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
        this.endpoint = URI.create(properties.baseUrl().toString().replaceAll("/+$", "") + "/chat/completions");
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    @Override
    public String complete(LlmRequest request) {
        return completeWithReasoningFlag(request).content();
    }

    /**
     * 응답 내용과 함께 reasoning_content(생각 모드 출력)가 있었는지만 돌려준다. 생각 내용 자체는 버린다.
     * 생각 모드가 실제로 꺼졌는지 확인하는 @Tag("llm") 테스트에서 쓴다.
     */
    Completion completeWithReasoningFlag(LlmRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(properties.timeout())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(request)), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(httpRequest);
        if (response.statusCode() / 100 != 2) {
            throw new LlmCallException(LlmFailureKind.HTTP_ERROR, "LLM call failed with status " + response.statusCode());
        }
        return completion(response.body());
    }

    /** 응답 내용과 reasoning_content가 있었는지. toString에 내용을 넣지 않는다. */
    record Completion(String content, boolean reasoningPresent) {

        @Override
        public String toString() {
            return "Completion[contentLength=" + content.length() + ", reasoningPresent=" + reasoningPresent + "]";
        }
    }

    private Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userContent())));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", request.schemaName(), "strict", true, "schema", request.schema())));
        // 같은 요청이면 같은 결과가 나오도록 샘플링을 고정한다 (ADR 0002)
        body.put("temperature", properties.temperature());
        body.put("seed", properties.seed());
        if (properties.disableThinking()) {
            body.put("chat_template_kwargs", Map.of("enable_thinking", false));
        }
        return body;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LlmFailureKind kind = classify(e);
            throw new LlmCallException(kind, kind == LlmFailureKind.TIMEOUT ? "LLM call timed out" : "LLM connection failed");
        } catch (InterruptedException e) {
            // 서버 종료로 작업자가 중단됨. 중단 표시는 유지한다
            Thread.currentThread().interrupt();
            throw new LlmCallException(LlmFailureKind.INTERRUPTED, "LLM call interrupted");
        }
    }

    /**
     * 연결하지 못함(연결 거부, 연결 타임아웃)과 응답 도중 끊김은 연결 실패, 요청 타임아웃은 타임아웃.
     * HttpConnectTimeoutException은 HttpTimeoutException의 하위 클래스라 먼저 본다.
     */
    static LlmFailureKind classify(IOException e) {
        if (e instanceof HttpConnectTimeoutException || e instanceof ConnectException) {
            return LlmFailureKind.CONNECTION_REFUSED;
        }
        if (e instanceof HttpTimeoutException) {
            return LlmFailureKind.TIMEOUT;
        }
        return LlmFailureKind.CONNECTION_REFUSED;
    }

    /**
     * choices[0].message.content와 reasoning_content가 비어 있지 않은지.
     * 형식이 아니면 응답 내용을 메시지에 넣지 않고 실패로 본다.
     */
    private Completion completion(String responseBody) {
        JsonNode message;
        try {
            message = json.readTree(responseBody).path("choices").path(0).path("message");
        } catch (JacksonException e) {
            throw new LlmCallException(LlmFailureKind.MALFORMED_RESPONSE, "LLM response is not JSON");
        }
        JsonNode content = message.path("content");
        if (!content.isString()) {
            throw new LlmCallException(LlmFailureKind.MALFORMED_RESPONSE, "LLM response has no message content");
        }
        JsonNode reasoning = message.path("reasoning_content");
        boolean reasoningPresent = reasoning.isString() && !reasoning.asString().isBlank();
        return new Completion(content.asString(), reasoningPresent);
    }
}
