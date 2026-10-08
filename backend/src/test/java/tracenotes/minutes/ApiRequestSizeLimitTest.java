package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** SPEC F1: 요청 본문이 200KB(204,800바이트)를 넘는 미리보기·작업 생성 요청은 413 (Content-Length가 있는 경우). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiRequestSizeLimitTest {

    private static final int MAX_BYTES = 200 * 1024;

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void previewOver200KbReturns413WithContentLength() throws Exception {
        HttpResponse<String> response = post("/api/preview", jsonOfExactBytes(MAX_BYTES + 1));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void jobOver200KbReturns413WithContentLength() throws Exception {
        HttpResponse<String> response = post("/api/jobs", jsonOfExactBytes(MAX_BYTES + 1));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    /** {"text":"가…"} 뒤를 JSON 공백으로 채워 정확히 size 바이트. 글자 수는 50,000 이하라 상한 필터가 없으면 통과하는 입력이다. */
    private static byte[] jsonOfExactBytes(int size) {
        String json = "{\"text\":\"" + "가".repeat(40_000) + "\"}";
        int padding = size - json.getBytes(StandardCharsets.UTF_8).length;
        return (json + " ".repeat(padding)).getBytes(StandardCharsets.UTF_8);
    }

    /** ofByteArray는 Content-Length를 붙여 보낸다. */
    private HttpResponse<String> post(String path, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofByteArray(body))
                .build();
        return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
