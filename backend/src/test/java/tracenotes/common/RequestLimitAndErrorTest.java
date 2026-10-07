package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tracenotes.minutes.ScriptValidator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RequestLimitAndErrorTest.ProbeController.class)
class RequestLimitAndErrorTest {

    /** SPEC S3: 요청 본문 바이트 상한 200KB (200 × 1024). */
    private static final int MAX_BYTES = 200 * 1024;
    private static final String MARKER = "비밀안건표식";

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    /** 시험용 엔드포인트. 실제 API(W4 미리보기, W6 작업 생성)가 쓸 검사·오류 흐름을 똑같이 탄다. */
    @RestController
    static class ProbeController {

        private final ScriptValidator validator;

        ProbeController(AppProperties properties) {
            this.validator = new ScriptValidator(properties.maxScriptChars());
        }

        /** 컨트롤러까지 도달한 요청 수. 상한 초과 요청이 여기까지 오지 않는지 확인한다. */
        static final AtomicInteger CALLS = new AtomicInteger();

        record ProbeRequest(String text) {
        }

        @PostMapping("/api/test-probe")
        Map<String, Integer> probe(@RequestBody ProbeRequest request) {
            CALLS.incrementAndGet();
            validator.validate(request.text());
            return Map.of("length", request.text().length());
        }

        /** 처리되지 않은 예외. 메시지에 입력 표식을 넣어 응답으로 새는지 본다. */
        @PostMapping("/api/test-probe/boom")
        Map<String, Integer> boom(@RequestBody ProbeRequest request) {
            throw new IllegalStateException("boom " + request.text());
        }
    }

    @Test
    void bodyOver200KbReturns413WithContentLength() throws Exception {
        HttpResponse<String> response = post(BodyPublishers.ofByteArray(jsonOfExactBytes(MAX_BYTES + 1)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void bodyOver200KbReturns413WhenChunked() throws Exception {
        byte[] body = jsonOfExactBytes(MAX_BYTES + 1);

        HttpResponse<String> response = post(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    /** 3MB: 413을 받거나 서버가 연결을 끊는다. 어느 경우든 컨트롤러는 호출되지 않는다. */
    @Test
    void body3MbIsRejectedBeforeController() throws Exception {
        byte[] body = jsonOfExactBytes(3 * 1024 * 1024);
        ProbeController.CALLS.set(0);

        String withLength = outcome(() -> post(BodyPublishers.ofByteArray(body)));
        String chunked = outcome(() -> post(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))));

        System.out.println("3MB Content-Length: " + withLength);
        System.out.println("3MB chunked: " + chunked);
        assertThat(withLength).isIn("413", "connection closed");
        assertThat(chunked).isIn("413", "connection closed");
        assertThat(ProbeController.CALLS.get()).isZero();
    }

    @Test
    void unhandledExceptionReturns500WithoutDetails() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/test-probe/boom"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"text\":\"" + MARKER + "\"}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"")
                .doesNotContain(MARKER)
                .doesNotContain("boom")
                .doesNotContain("IllegalStateException")
                .doesNotContain("Exception")
                .doesNotContain("tracenotes.");
    }

    @Test
    void bodyOfExactly200KbIsAccepted() throws Exception {
        byte[] body = jsonOfExactBytes(MAX_BYTES);

        HttpResponse<String> withLength = post(BodyPublishers.ofByteArray(body));
        HttpResponse<String> chunked = post(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));

        assertThat(withLength.statusCode()).isEqualTo(200);
        assertThat(chunked.statusCode()).isEqualTo(200);
    }

    @Test
    void errorResponseDoesNotEchoInput() throws Exception {
        HttpResponse<String> nul = postJson("{\"text\":\"" + MARKER + "\\u0000\"}");
        HttpResponse<String> blank = postJson("{\"text\":\"   \"}");
        HttpResponse<String> tooLong = postJson("{\"text\":\"" + MARKER + "가".repeat(50_000) + "\"}");
        HttpResponse<String> malformed = postJson("{\"text\":\"" + MARKER + "\"");
        HttpResponse<String> wrongType = postJson("{\"text\":[\"" + MARKER + "\"]}");

        assertThat(nul.statusCode()).isEqualTo(400);
        assertThat(nul.body()).contains("\"code\":\"CONTAINS_NUL\"");
        assertThat(blank.statusCode()).isEqualTo(400);
        assertThat(blank.body()).contains("\"code\":\"BLANK\"");
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(tooLong.body()).contains("\"code\":\"TOO_LONG\"");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformed.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
        assertThat(wrongType.statusCode()).isEqualTo(400);
        assertThat(wrongType.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
        for (HttpResponse<String> response : java.util.List.of(nul, blank, tooLong, malformed, wrongType)) {
            assertThat(response.body()).doesNotContain(MARKER).doesNotContain("Exception");
        }
    }

    @Test
    void methodNotAllowedKeepsStatusAndAllowHeader() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/test-probe"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow")).hasValueSatisfying(allow -> assertThat(allow).contains("POST"));
        assertThat(response.body()).contains("\"code\":\"REQUEST_ERROR\"");
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        HttpResponse<String> allowed = preflight("http://localhost:5173");
        HttpResponse<String> denied = preflight("http://evil.example");

        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
        assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(denied.statusCode()).isEqualTo(403);
    }

    private interface Send {
        HttpResponse<String> send() throws Exception;
    }

    /** 응답 코드 또는 연결 종료("connection closed"). */
    private static String outcome(Send send) throws Exception {
        try {
            return String.valueOf(send.send().statusCode());
        } catch (IOException e) {
            return "connection closed";
        }
    }

    /** {"text":"가…"} 뒤를 JSON 공백으로 채워 정확히 size 바이트. 글자 수는 50,000 이하. */
    private static byte[] jsonOfExactBytes(int size) {
        String json = "{\"text\":\"" + "가".repeat(40_000) + "\"}";
        int padding = size - json.getBytes(StandardCharsets.UTF_8).length;
        return (json + " ".repeat(padding)).getBytes(StandardCharsets.UTF_8);
    }

    private HttpResponse<String> postJson(String json) throws Exception {
        return post(BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(HttpRequest.BodyPublisher body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/test-probe"))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();
        return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/test-probe"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", BodyPublishers.noBody())
                .build();
        return client.send(request, BodyHandlers.ofString());
    }
}
