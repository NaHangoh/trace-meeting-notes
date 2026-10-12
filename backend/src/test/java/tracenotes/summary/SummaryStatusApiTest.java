package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tracenotes.minutes.JobRepository;
import tracenotes.minutes.Utterance;

/**
 * SPEC F2 조회 API: GET /api/jobs/{id} → {status, failure?, items?}. 발언 본문은 넣지 않는다.
 * 형식이 틀린 ID, 없는 ID, 보관 기간이 지나 삭제된 ID는 같은 404이고, 모두 같은 DB 조회를 거친다.
 * 작업 ID와 항목 내용은 로그에 남지 않는다. Spring 웹·앱 DEBUG 로그까지 켜서 확인한다.
 */
@SpringBootTest(properties = {
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.tracenotes=DEBUG"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class SummaryStatusApiTest {

    private static final String UTTERANCE_MARKER = "utteranceBodyMarker";
    private static final String SPEAKER_MARKER = "speakerMarker";
    private static final String ITEM_MARKER = "itemTextMarker";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    JobRepository jobs;

    @MockitoSpyBean
    SummaryRepository repository;

    @Autowired
    LoggingSystem loggingSystem;

    /**
     * 같은 설정의 컨텍스트를 다른 테스트와 함께 쓰면, 사이에 뜬 다른 컨텍스트가 로그 레벨을 기본값으로 되돌린다.
     * 실행 순서와 상관없이 DEBUG에서 확인하도록 테스트마다 레벨을 다시 정한다.
     */
    @BeforeEach
    void enableDebugLogs() {
        loggingSystem.setLogLevel("org.springframework.web", LogLevel.DEBUG);
        loggingSystem.setLogLevel("tracenotes", LogLevel.DEBUG);
    }

    @AfterEach
    void resetSpy() {
        reset(repository);
    }

    private static String newIp() {
        return "198.51.100." + (100 + NEXT_IP.incrementAndGet());
    }

    private MvcResult getJob(String id) throws Exception {
        return mvc.perform(get("/api/jobs/" + id).with(r -> { r.setRemoteAddr(newIp()); return r; })).andReturn();
    }

    private String insertJob(Instant createdAt) {
        String id = UUID.randomUUID().toString();
        jobs.insert(id, createdAt, List.of(
                new Utterance(1, SPEAKER_MARKER, UTTERANCE_MARKER + " 참석자A가 다음 주까지 정리합니다"),
                new Utterance(2, "참석자", UTTERANCE_MARKER + " 좋습니다")));
        return id;
    }

    private String doneJob() {
        String id = insertJob(Instant.now());
        repository.markRunning(id);
        repository.saveDone(id, List.of(
                new SummaryItem(SummaryItemType.TODO, ITEM_MARKER + " 정리", List.of(1), "참석자A", "다음 주"),
                new SummaryItem(SummaryItemType.DECISION, "근거 없는 결정", List.of(), null, null)));
        return id;
    }

    @Test
    void queuedJobHasStatusOnly() throws Exception {
        String id = insertJob(Instant.now());

        mvc.perform(get("/api/jobs/" + id).with(r -> { r.setRemoteAddr(newIp()); return r; }))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"QUEUED\"}", JsonCompareMode.STRICT));
    }

    @Test
    void failedJobHasStatusAndFailureOnly() throws Exception {
        String id = insertJob(Instant.now());
        repository.markFailed(id, SummaryFailure.TIMEOUT);

        mvc.perform(get("/api/jobs/" + id).with(r -> { r.setRemoteAddr(newIp()); return r; }))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"FAILED\",\"failure\":\"TIMEOUT\"}", JsonCompareMode.STRICT));
    }

    /** 응답에는 상태·항목(종류, 내용, 근거 번호, 담당자, 기한)만 있다. 발언 본문과 화자 이름은 없다. */
    @Test
    void statusResponseHasNoUtteranceText() throws Exception {
        String id = doneJob();

        MvcResult result = getJob(id);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(UTTERANCE_MARKER).doesNotContain(SPEAKER_MARKER);
        mvc.perform(get("/api/jobs/" + id).with(r -> { r.setRemoteAddr(newIp()); return r; }))
                .andExpect(content().json("""
                        {"status":"DONE","items":[
                          {"type":"TODO","text":"%s 정리","evidence":[1],"assignee":"참석자A","due":"다음 주"},
                          {"type":"DECISION","text":"근거 없는 결정","evidence":[],"assignee":null,"due":null}
                        ]}""".formatted(ITEM_MARKER), JsonCompareMode.STRICT));
    }

    /**
     * 형식이 틀린 ID, 없는 ID, 보관 기간이 지나 삭제된 ID(결과까지 있던 DONE 작업)는 같은 404 응답이다.
     * 형식이 틀린 ID도 DB 조회를 거친다(바로 돌려주지 않음).
     */
    @Test
    void malformedUnknownAndDeletedIdSame404() throws Exception {
        String deleted = insertJob(Instant.parse("2000-01-01T00:00:00Z"));
        repository.markRunning(deleted);
        repository.saveDone(deleted, List.of(new SummaryItem(SummaryItemType.DECISION, "삭제될 결정", List.of(1), null, null)));
        assertThat(getJob(deleted).getResponse().getStatus()).isEqualTo(200);
        jobs.deleteCreatedBefore(Instant.parse("2001-01-01T00:00:00Z"));
        assertThat(repository.findItems(deleted)).isEmpty();
        String unknown = UUID.randomUUID().toString();
        String malformed = "not-a-job-id";
        String tooLong = "a".repeat(200);

        MvcResult expected = getJob(unknown);
        assertThat(expected.getResponse().getStatus()).isEqualTo(404);
        assertThat(expected.getResponse().getContentAsString()).isEqualTo("{\"code\":\"NOT_FOUND\",\"message\":\"작업을 찾을 수 없습니다.\"}");
        for (String id : List.of(deleted, malformed, tooLong, deleted.toUpperCase())) {
            MvcResult result = getJob(id);
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            assertThat(result.getResponse().getContentAsString()).isEqualTo(expected.getResponse().getContentAsString());
            assertThat(result.getResponse().getContentType()).isEqualTo(expected.getResponse().getContentType());
            assertThat(result.getResponse().getHeaderNames()).isEqualTo(expected.getResponse().getHeaderNames());
        }
        verify(repository).findState(malformed);
        verify(repository).findState(tooLong);
    }

    /** S1: 작업마다 자기 결과만 나온다. 다른 작업의 항목이 섞이지 않는다. */
    @Test
    void eachJobReturnsOnlyItsOwnItems() throws Exception {
        String first = insertJob(Instant.now());
        String second = insertJob(Instant.now());
        for (String id : List.of(first, second)) {
            repository.markRunning(id);
            repository.saveDone(id, List.of(new SummaryItem(SummaryItemType.DECISION, "결정 " + id, List.of(1), null, null)));
        }

        String firstBody = getJob(first).getResponse().getContentAsString();
        String secondBody = getJob(second).getResponse().getContentAsString();

        assertThat(firstBody).contains("결정 " + first).doesNotContain(second);
        assertThat(secondBody).contains("결정 " + second).doesNotContain(first);
    }

    /** S1: 작업 ID를 한 글자 바꾼 ID로는 결과를 볼 수 없다 (순번 대입). */
    @Test
    void neighbouringIdCannotReadAnotherJob() throws Exception {
        String id = doneJob();
        char last = id.charAt(id.length() - 1);
        for (char c : "0123456789abcdef".toCharArray()) {
            if (c == last) {
                continue;
            }
            String neighbour = id.substring(0, id.length() - 1) + c;
            assertThat(getJob(neighbour).getResponse().getStatus()).isEqualTo(404);
        }
    }

    /**
     * 작업 ID(곧 열람 권한)와 항목 내용은 앱·핸들러 DEBUG 로그(매핑, 응답 객체 toString, 예외 처리)에 남지 않는다.
     * MockMvc에서는 DispatcherServlet의 요청 줄(URI)이 찍히지 않아 이 테스트 범위 밖이다. 실제 서버에서
     * org.springframework.web DEBUG를 켜면 요청 URI 속 작업 ID가 남으므로 운영에서는 이 로거의 DEBUG를 켜지 않는다.
     */
    @Test
    void jobIdAndItemsNotInLogs(CapturedOutput output) throws Exception {
        String done = doneJob();
        String unknown = UUID.randomUUID().toString();

        assertThat(getJob(done).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJob(unknown).getResponse().getStatus()).isEqualTo(404);

        // DEBUG가 실제로 찍힌 상태에서 확인한다 (빈 검사가 되지 않게)
        assertThat(output.getAll()).contains("Writing [JobStatusResponse[status=DONE, failure=null, items=2]]")
                .contains("Resolved [tracenotes.summary.JobNotFoundException");
        assertThat(output.getAll()).doesNotContain(done).doesNotContain(unknown)
                .doesNotContain(ITEM_MARKER).doesNotContain("참석자A").doesNotContain(UTTERANCE_MARKER);
    }

    /** 조회 중 처리하지 못한 예외: 500 고정 응답, 로그에는 경로 패턴만 있고 URI 속 작업 ID는 없다. */
    @Test
    void unhandledStatusErrorDoesNotLogJobId(CapturedOutput output) throws Exception {
        String id = UUID.randomUUID().toString();
        doAnswer(invocation -> {
            throw new IllegalStateException("statusErrorMarker");
        }).when(repository).findState(any());

        MvcResult result = getJob(id);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).contains("INTERNAL_ERROR").doesNotContain(id);
        assertThat(output.getAll()).contains("/api/jobs/{id}").doesNotContain(id).doesNotContain("\tat ");
    }
}
