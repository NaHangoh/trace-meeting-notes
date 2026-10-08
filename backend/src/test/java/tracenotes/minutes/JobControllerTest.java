package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * SPEC F1 작업 생성, S1. 요청 본문과 작업 ID가 로그에 남는지 보려고 웹 계층 DEBUG 로그를 켠다.
 * MockMvc 자체의 요청·응답 출력은 테스트 도구 출력이라 끈다 (앱 로그만 검사).
 */
@SpringBootTest(properties = {
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.tracenotes=DEBUG"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(PreviewRateLimitTest.FixedClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class JobControllerTest {

    private static final String MARKER = "비밀안건표식";
    private static final String ASCII_MARKER = "secretAgendaMarker";
    private static final String SCRIPT = "첫 인사\\n김민수: 일정은요\\n다음 주까지 가능합니다\\n\\n이영희: 좋습니다";

    /** 테스트마다 다른 IP로 보내 작업 생성 제한(시간당 20회)에 걸리지 않게 한다. */
    private static final AtomicInteger NEXT_IP = new AtomicInteger(100);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String ip;

    @BeforeEach
    void newIp() {
        ip = "192.0.2." + NEXT_IP.incrementAndGet();
    }

    @Test
    void createsJobWithUuidV4AndStoresUtterances() throws Exception {
        MvcResult result = mvc.perform(job(SCRIPT))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = jobId(result);
        UUID uuid = UUID.fromString(jobId);
        assertThat(uuid.version()).isEqualTo(4);
        assertThat(uuid.variant()).isEqualTo(2);
        assertThat(uuid.toString()).isEqualTo(jobId);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"jobId\":\"" + jobId + "\"}");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT utterance_no, speaker, content FROM job_utterance WHERE job_id = ? ORDER BY utterance_no", jobId);
        assertThat(rows).extracting(r -> r.get("UTTERANCE_NO"), r -> r.get("SPEAKER"), r -> r.get("CONTENT"))
                .containsExactly(
                        tuple(1, "미상", "첫 인사"),
                        tuple(2, "김민수", "일정은요"),
                        tuple(3, "김민수", "다음 주까지 가능합니다"),
                        tuple(4, "이영희", "좋습니다"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job WHERE id = ?", Integer.class, jobId)).isEqualTo(1);
    }

    /**
     * 보충 평면 문자(UTF-16 두 단위)로 채운 최대 길이 한 줄도 저장된다.
     * 글자 수는 코드 포인트로 세지만 H2 VARCHAR 길이는 UTF-16 단위라, 열 길이가 짧으면 저장에서 500이 난다.
     */
    @Test
    void storesMaxLengthLineOfSupplementaryCharacters() throws Exception {
        String prefix = "김민수: ";
        String text = new String(Character.toChars(0x1F600)).repeat(50_000 - prefix.length());
        String script = prefix + text;
        assertThat(script.codePointCount(0, script.length())).isEqualTo(50_000);

        MvcResult result = mvc.perform(raw("{\"text\":\"" + script + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String content = jdbc.queryForObject(
                "SELECT content FROM job_utterance WHERE job_id = ?", String.class, jobId(result));
        assertThat(content).isEqualTo(text);
    }

    /** 저장되는 created_at은 주입된 시계의 시각이다 (보관 기간 삭제 S2의 기준). */
    @Test
    void createdAtIsClockInstant() throws Exception {
        String jobId = jobId(mvc.perform(job(SCRIPT)).andExpect(status().isCreated()).andReturn());

        OffsetDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM job WHERE id = ?", OffsetDateTime.class, jobId);
        assertThat(createdAt.toInstant()).isEqualTo(RetentionCleanupJobTest.NOW);
    }

    @Test
    void jobIdsAreDistinct() throws Exception {
        String first = jobId(mvc.perform(job(SCRIPT)).andExpect(status().isCreated()).andReturn());
        String second = jobId(mvc.perform(job(SCRIPT)).andExpect(status().isCreated()).andReturn());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void previewMatchesJobSplit() throws Exception {
        String script = "김민수: 시작합니다\\n다음 회의는 10:30으로 하죠\\n참고: 마감 주의\\n2024: 목표\\n참석자 1: 네\\n김민수  팀장: 네";

        MvcResult preview = mvc.perform(post("/api/preview")
                        .with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("{\"text\":\"" + script + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String jobId = jobId(mvc.perform(job(script)).andExpect(status().isCreated()).andReturn());

        List<Utterance> stored = jdbc.queryForList(
                        "SELECT speaker FROM job_utterance WHERE job_id = ? ORDER BY utterance_no", String.class, jobId)
                .stream()
                .map(speaker -> new Utterance(0, speaker, ""))
                .toList();
        String previewJson = preview.getResponse().getContentAsString();
        int previewCount = JsonPath.read(previewJson, "$.utteranceCount");
        List<String> previewSpeakers = JsonPath.read(previewJson, "$.speakers");
        assertThat(stored).hasSize(previewCount);
        assertThat(ScriptSplitter.speakersOf(stored)).isEqualTo(previewSpeakers);
    }

    @Test
    void createJobRejectsBlankOverLengthNul() throws Exception {
        int jobsBefore = count("job");

        mvc.perform(job("  \\n\\u00A0\\n"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BLANK"));
        mvc.perform(job(MARKER + "가".repeat(50_000)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_LONG"));
        mvc.perform(job("김민수: " + MARKER + "\\u0000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTAINS_NUL"));

        assertThat(count("job")).isEqualTo(jobsBefore);
    }

    /** SPEC F1: 50,000자를 넘는 작업 생성 요청은 400이고, 오류 응답에 입력 본문을 넣지 않는다. */
    @Test
    void createJobOverLengthErrorDoesNotEchoInput() throws Exception {
        MvcResult result = mvc.perform(job(MARKER + "가".repeat(50_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_LONG"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain(MARKER)
                .doesNotContain("가가가");
    }

    @Test
    void createJobRejectsNoUtterances() throws Exception {
        int jobsBefore = count("job");

        mvc.perform(job("김민수:\\n\\n이영희:   "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NO_UTTERANCES"));

        assertThat(count("job")).isEqualTo(jobsBefore);
    }

    /** 작업 ID가 곧 열람 권한이므로 로그에 남기지 않는다. */
    @Test
    void jobIdNotInLogs(CapturedOutput output) throws Exception {
        String jobId = jobId(mvc.perform(job(SCRIPT)).andExpect(status().isCreated()).andReturn());

        // DEBUG 로그가 실제로 켜져 있고 응답 객체가 로그에 찍히는지 확인해야 "남지 않음"이 의미가 있다
        assertThat(output.getAll()).contains("JobController#create").contains("Writing [JobCreated[");
        assertThat(output.getAll()).doesNotContain(jobId);
    }

    /** 발언 내용, 화자 이름, 깨진 JSON 모두 로그에 남지 않는다. */
    @Test
    void jobCreationDoesNotLogInputBody(CapturedOutput output) throws Exception {
        mvc.perform(job("김민수: " + MARKER)).andExpect(status().isCreated());
        mvc.perform(job(MARKER + ": 내용")).andExpect(status().isCreated());
        mvc.perform(job("김민수: " + MARKER + "\\u0000")).andExpect(status().isBadRequest());
        mvc.perform(raw("{\"text\": " + ASCII_MARKER + "}")).andExpect(status().isBadRequest());

        assertThat(output.getAll()).contains("JobController#create").contains("Read \"application/json");
        assertThat(output.getAll()).doesNotContain(MARKER).doesNotContain(ASCII_MARKER);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String jobId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.jobId");
    }

    /** text는 JSON 문자열 안에 그대로 들어간다(이스케이프 포함). */
    private MockHttpServletRequestBuilder job(String jsonEscapedText) {
        return raw("{\"text\":\"" + jsonEscapedText + "\"}");
    }

    private MockHttpServletRequestBuilder raw(String body) {
        return post("/api/jobs")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(body);
    }
}
