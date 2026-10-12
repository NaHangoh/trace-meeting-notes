package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tracenotes.TraceNotesApplication;
import tracenotes.llm.FixtureLlmClient;
import tracenotes.llm.LlmClient;
import tracenotes.minutes.JobRepository;
import tracenotes.minutes.JobService;
import tracenotes.minutes.Utterance;

/**
 * SPEC F2 동시 작업·다시 시도, ADR 0007. 작업자 1개, 대기열 상한 2(테스트 설정)로 실제 대기열과 작업자를 거친다.
 * LLM은 FixtureLlmClient이고, release를 열 때까지 응답을 막아 실행 중 상태를 만든다.
 */
@SpringBootTest(properties = "app.summary.queue-capacity=2")
@AutoConfigureMockMvc
class SummaryJobFlowTest {

    static final String RESULT = "{\"items\":[{\"type\":\"DECISION\",\"text\":\"결정\",\"evidence\":[1],"
            + "\"assignee\":null,\"due\":null}]}";

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LlmClient llm;

    @Autowired
    SummaryRepository repository;

    @Autowired
    JobRepository jobs;

    @Autowired
    JobService jobService;

    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> created = new CopyOnWriteArrayList<>();

    @BeforeEach
    void blockLlm() {
        ((FixtureLlmClient) llm).respondWith(request -> {
            calls.incrementAndGet();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return RESULT;
        });
    }

    /** 다음 테스트에 실행 중 작업이 남지 않게 막아 둔 응답을 풀고 끝날 때까지 기다린다. */
    @AfterEach
    void drain() throws Exception {
        release.countDown();
        for (String id : created) {
            awaitStatus(repository, id, JobStatus.DONE, JobStatus.FAILED);
        }
        ((FixtureLlmClient) llm).reset();
    }

    static JobState awaitStatus(SummaryRepository repository, String jobId, JobStatus... expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            JobState state = repository.findState(jobId).orElseThrow();
            if (List.of(expected).contains(state.status()) || System.nanoTime() > deadline) {
                assertThat(state.status()).isIn((Object[]) expected);
                return state;
            }
            Thread.sleep(5);
        }
    }

    private static String newIp() {
        return "198.51.100." + NEXT_IP.incrementAndGet();
    }

    static MockHttpServletRequestBuilder createRequest(String ip) {
        return post("/api/jobs")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("{\"text\":\"참석자: 다음 주에 배포합니다\"}");
    }

    static MockHttpServletRequestBuilder retryRequest(String jobId, String ip) {
        return post("/api/jobs/" + jobId + "/retry").with(r -> { r.setRemoteAddr(ip); return r; });
    }

    private String createJob() throws Exception {
        String body = mvc.perform(createRequest(newIp()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.jobId");
        created.add(id);
        return id;
    }

    /** 대기열을 거치지 않고 저장만 한 실패 작업. */
    private String failedJob(SummaryFailure failure) {
        String id = UUID.randomUUID().toString();
        jobs.insert(id, Instant.now(), List.of(new Utterance(1, "참석자", "다음 주에 배포합니다")));
        repository.markFailed(id, failure);
        created.add(id);
        return id;
    }

    private int jobCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM job", Integer.class);
    }

    @Test
    void queuedRunningDoneLifecycle() throws Exception {
        String first = createJob();
        awaitStatus(repository, first, JobStatus.RUNNING);
        String second = createJob();

        // 작업자 1개: 앞 작업이 끝나기 전에는 다음 작업이 시작하지 않는다
        Thread.sleep(100);
        assertThat(repository.findState(second)).contains(new JobState(JobStatus.QUEUED, null));
        assertThat(calls).hasValue(1);

        release.countDown();

        assertThat(awaitStatus(repository, first, JobStatus.DONE)).isEqualTo(new JobState(JobStatus.DONE, null));
        awaitStatus(repository, second, JobStatus.DONE);
        assertThat(repository.findItems(first)).singleElement()
                .isEqualTo(new SummaryItem(SummaryItemType.DECISION, "결정", List.of(1), null, null));
    }

    @Test
    void queueFullReturns429QueueFullAndCreatesNoJob() throws Exception {
        String running = createJob();
        awaitStatus(repository, running, JobStatus.RUNNING);
        createJob();
        createJob();
        int before = jobCount();

        mvc.perform(createRequest(newIp()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));

        assertThat(jobCount()).isEqualTo(before);
    }

    @Test
    void concurrentCreationNeverExceedsQueueLimit() throws Exception {
        String running = createJob();
        awaitStatus(repository, running, JobStatus.RUNNING);
        int before = jobCount();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<String>> results = new ArrayList<>();
        Callable<String> create = () -> {
            start.await();
            return jobService.create("참석자: 동시에 만듭니다");
        };
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(create));
        }

        start.countDown();
        int succeeded = 0;
        int queueFull = 0;
        for (Future<String> result : results) {
            try {
                created.add(result.get(10, TimeUnit.SECONDS));
                succeeded++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(QueueFullException.class);
                queueFull++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(2);
        assertThat(queueFull).isEqualTo(18);
        assertThat(jobCount()).isEqualTo(before + 2);
    }

    @Test
    void retryOnlyForFailed409Otherwise() throws Exception {
        release.countDown();
        String failed = failedJob(SummaryFailure.TIMEOUT);

        mvc.perform(retryRequest(failed, newIp())).andExpect(status().isAccepted()).andExpect(content().string(""));

        awaitStatus(repository, failed, JobStatus.DONE);
        assertThat(repository.findItems(failed)).hasSize(1);
        mvc.perform(retryRequest(failed, newIp()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_RETRYABLE"));
    }

    /** 기다리는·실행 중인 작업은 다시 시도할 수 없고(409), 상태와 대기열 자리가 그대로다. */
    @Test
    void retryQueuedOrRunningIs409AndStateUnchanged() throws Exception {
        String running = createJob();
        awaitStatus(repository, running, JobStatus.RUNNING);
        String queued = createJob();

        for (String id : List.of(running, queued)) {
            mvc.perform(retryRequest(id, newIp()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NOT_RETRYABLE"));
        }

        assertThat(repository.findState(running)).contains(new JobState(JobStatus.RUNNING, null));
        assertThat(repository.findState(queued)).contains(new JobState(JobStatus.QUEUED, null));
        // 409가 자리를 잡고 돌려주지 않았다면 상한 2에서 남은 한 자리가 없다
        createJob();
        mvc.perform(createRequest(newIp())).andExpect(jsonPath("$.code").value("QUEUE_FULL"));
    }

    /** 다시 시도는 그 작업에 저장된 발언으로 다시 정리한다. */
    @Test
    void retrySummarizesSameUtterances() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        ((FixtureLlmClient) llm).respondWith(request -> {
            requests.add(request.userContent());
            return RESULT;
        });
        String failed = UUID.randomUUID().toString();
        jobs.insert(failed, Instant.now(), List.of(
                new Utterance(1, "기획", "다시 시도 첫 발언"),
                new Utterance(2, "개발", "다시 시도 둘째 발언")));
        repository.markFailed(failed, SummaryFailure.TIMEOUT);
        created.add(failed);
        String other = failedJob(SummaryFailure.TIMEOUT);

        mvc.perform(retryRequest(failed, newIp())).andExpect(status().isAccepted());
        awaitStatus(repository, failed, JobStatus.DONE);

        assertThat(requests).singleElement().satisfies(content -> assertThat(content)
                .contains("[1] 기획: 다시 시도 첫 발언", "[2] 개발: 다시 시도 둘째 발언")
                .doesNotContain("다음 주에 배포합니다"));
        assertThat(repository.findState(other)).contains(new JobState(JobStatus.FAILED, SummaryFailure.TIMEOUT));
    }

    /** 형식이 틀린 ID와 없는 ID는 같은 404다. */
    @Test
    void retryUnknownOrMalformedIdIsSame404() throws Exception {
        String unknown = mvc.perform(retryRequest(UUID.randomUUID().toString(), newIp()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String malformed = mvc.perform(retryRequest("not-a-job-id", newIp()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(malformed).isEqualTo(unknown).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void retryRespectsQueueLimit() throws Exception {
        String failed = failedJob(SummaryFailure.CONNECTION_REFUSED);
        String running = createJob();
        awaitStatus(repository, running, JobStatus.RUNNING);
        createJob();
        createJob();

        mvc.perform(retryRequest(failed, newIp()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));

        assertThat(repository.findState(failed))
                .contains(new JobState(JobStatus.FAILED, SummaryFailure.CONNECTION_REFUSED));
    }

    /** 서버를 다시 시작하면 QUEUED·RUNNING 작업은 FAILED(SERVER_RESTART)가 되고, 다시 시도할 수 있다. */
    @Test
    void restartMarksQueuedAndRunningFailedAndTheyCanRetry() throws Exception {
        release.countDown();
        String queued = UUID.randomUUID().toString();
        String running = UUID.randomUUID().toString();
        String done = UUID.randomUUID().toString();
        String failed = failedJob(SummaryFailure.TIMEOUT);
        for (String id : List.of(queued, running, done)) {
            jobs.insert(id, Instant.now(), List.of(new Utterance(1, "참석자", "다음 주에 배포합니다")));
            created.add(id);
        }
        repository.markRunning(running);
        repository.markRunning(done);
        repository.saveDone(done, List.of());

        // 같은 DB로 애플리케이션을 한 번 더 시작한다 (재시작)
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(TraceNotesApplication.class)
                .web(WebApplicationType.NONE).run()) {
            assertThat(restarted.isRunning()).isTrue();
        }

        assertThat(repository.findState(queued)).contains(new JobState(JobStatus.FAILED, SummaryFailure.SERVER_RESTART));
        assertThat(repository.findState(running)).contains(new JobState(JobStatus.FAILED, SummaryFailure.SERVER_RESTART));
        assertThat(repository.findState(done)).contains(new JobState(JobStatus.DONE, null));
        assertThat(repository.findState(failed)).contains(new JobState(JobStatus.FAILED, SummaryFailure.TIMEOUT));

        mvc.perform(retryRequest(queued, newIp())).andExpect(status().isAccepted());
        awaitStatus(repository, queued, JobStatus.DONE);
    }
}
