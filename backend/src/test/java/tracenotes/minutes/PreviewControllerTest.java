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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** SPEC F1 미리보기 API. 요청 본문이 로그에 남는지 보려고 웹 계층 DEBUG 로그를 켠다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.tracenotes=DEBUG"
})
@ExtendWith(OutputCaptureExtension.class)
class PreviewControllerTest {

    private static final String MARKER = "비밀안건표식";
    private static final String ASCII_MARKER = "secretAgendaMarker";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void returnsCountAndSpeakersOnly() throws Exception {
        HttpResponse<String> response = preview("첫 인사\\n김민수: 일정은요\\n다음 주까지 가능합니다\\n이영희: " + MARKER);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .isEqualTo("{\"utteranceCount\":4,\"speakers\":[\"김민수\",\"이영희\",\"미상\"]}");
    }

    @Test
    void blankReturns400() throws Exception {
        HttpResponse<String> response = preview("  \\n\\u00A0\\n");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"BLANK\"");
    }

    @Test
    void overLengthReturns400() throws Exception {
        HttpResponse<String> response = preview(MARKER + "가".repeat(50_000));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"TOO_LONG\"").doesNotContain(MARKER);
    }

    @Test
    void nulReturns400() throws Exception {
        HttpResponse<String> response = preview("김민수: " + MARKER + "\\u0000");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"CONTAINS_NUL\"").doesNotContain(MARKER);
    }

    @Test
    void noUtterancesReturns400() throws Exception {
        HttpResponse<String> response = preview("김민수:\\n\\n이영희:   ");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"NO_UTTERANCES\"");
    }

    @Test
    void doesNotPersistAnything() throws Exception {
        int jobsBefore = count("job");
        int utterancesBefore = count("job_utterance");

        HttpResponse<String> response = preview("김민수: " + MARKER);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(count("job")).isEqualTo(jobsBefore);
        assertThat(count("job_utterance")).isEqualTo(utterancesBefore);
    }

    @Test
    void acceptsExactlyMaxLength() throws Exception {
        HttpResponse<String> response = preview("가".repeat(50_000));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    /** 발언 내용, 화자 이름(응답에 들어가는 값), 깨진 JSON 모두 로그에 남지 않는다. */
    @Test
    void doesNotLogInputBody(CapturedOutput output) throws Exception {
        HttpResponse<String> ok = preview("김민수: " + MARKER);
        HttpResponse<String> speaker = preview(MARKER + ": 내용");
        HttpResponse<String> nul = preview("김민수: " + MARKER + "\\u0000");
        HttpResponse<String> malformed = postRaw("{\"text\": " + MARKER + "}");
        // 한글은 UTF-8 해석 단계에서 멈추므로, Jackson이 토큰을 오류 메시지에 넣는 경로는 ASCII로 확인한다
        HttpResponse<String> malformedAscii = postRaw("{\"text\": " + ASCII_MARKER + "}");

        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(speaker.statusCode()).isEqualTo(200);
        assertThat(nul.statusCode()).isEqualTo(400);
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformedAscii.statusCode()).isEqualTo(400);
        assertThat(output.getAll()).doesNotContain(ASCII_MARKER);
        // DEBUG 로그가 실제로 켜져 있는지 확인해야 "남지 않음"이 의미가 있다
        assertThat(output.getAll()).contains("/api/preview");
        assertThat(output.getAll()).doesNotContain(MARKER);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    /** text는 JSON 문자열 안에 그대로 들어간다(이스케이프 포함). */
    private HttpResponse<String> preview(String jsonEscapedText) throws Exception {
        return postRaw("{\"text\":\"" + jsonEscapedText + "\"}");
    }

    private HttpResponse<String> postRaw(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/preview"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
