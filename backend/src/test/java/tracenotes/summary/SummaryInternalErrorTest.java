package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tracenotes.summary.SummaryJobFlowTest.awaitStatus;
import static tracenotes.summary.SummaryJobFlowTest.createRequest;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tracenotes.llm.FixtureLlmClient;
import tracenotes.llm.LlmClient;

/**
 * SPEC F2 실패 처리: 결과 저장 실패·요청 조립 오류 같은 예상 밖 오류는 FAILED(INTERNAL_ERROR)이고 자동으로 다시 실행하지 않는다.
 * 예외 메시지에는 SQL과 작업 ID가 들어갈 수 있으므로 로그·응답에는 실패 종류만 남는다. 앱 DEBUG 로그까지 켜서 확인한다.
 */
@SpringBootTest(properties = {
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.tracenotes=DEBUG"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class SummaryInternalErrorTest {

    private static final String SQL_MARKER = "INSERT INTO summary_evidence";
    private static final String MESSAGE_MARKER = "internalDetailMarker";

    @Autowired
    MockMvc mvc;

    @Autowired
    LlmClient llm;

    @MockitoSpyBean
    SummaryRepository repository;

    private final AtomicInteger calls = new AtomicInteger();

    @AfterEach
    void resetFixture() {
        ((FixtureLlmClient) llm).reset();
        reset(repository);
    }

    private String createJob(String ip) throws Exception {
        String body = mvc.perform(createRequest(ip))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.jobId");
    }

    private void respondWithResult() {
        ((FixtureLlmClient) llm).respondWith(request -> {
            calls.incrementAndGet();
            return SummaryJobFlowTest.RESULT;
        });
    }

    @Test
    void saveFailureIsInternalErrorWithoutDetails(CapturedOutput output) throws Exception {
        respondWithResult();
        // DB 제약 위반 예외처럼 메시지에 SQL과 작업 ID를 담는다
        doAnswer(invocation -> {
            throw new DataIntegrityViolationException(SQL_MARKER + " VALUES ('" + invocation.getArgument(0) + "', 1)");
        }).when(repository).saveDone(any(), anyList());

        String jobId = createJob("203.0.113.10");

        assertThat(awaitStatus(repository, jobId, JobStatus.FAILED))
                .isEqualTo(new JobState(JobStatus.FAILED, SummaryFailure.INTERNAL_ERROR));
        assertThat(calls).as("자동으로 다시 실행하지 않음").hasValue(1);
        assertThat(output.getAll()).contains("INTERNAL_ERROR")
                .doesNotContain(SQL_MARKER).doesNotContain(jobId).doesNotContain("\tat ");
    }

    /** 요청 조립 등 정리 중 예상 밖 예외도 같다. */
    @Test
    void unexpectedExceptionDuringSummaryIsInternalErrorWithoutDetails(CapturedOutput output) throws Exception {
        ((FixtureLlmClient) llm).respondWith(request -> {
            calls.incrementAndGet();
            throw new IllegalStateException(MESSAGE_MARKER);
        });

        String jobId = createJob("203.0.113.11");

        assertThat(awaitStatus(repository, jobId, JobStatus.FAILED))
                .isEqualTo(new JobState(JobStatus.FAILED, SummaryFailure.INTERNAL_ERROR));
        assertThat(calls).hasValue(1);
        assertThat(output.getAll()).contains("INTERNAL_ERROR")
                .doesNotContain(MESSAGE_MARKER).doesNotContain(jobId).doesNotContain("\tat ");
    }

    /** 다시 시도 중 처리하지 못한 예외: 500 고정 응답, 로그에는 경로 패턴만 있고 URI 속 작업 ID는 없다. */
    @Test
    void unhandledRetryErrorDoesNotLogJobId(CapturedOutput output) throws Exception {
        String jobId = java.util.UUID.randomUUID().toString();
        doAnswer(invocation -> {
            throw new IllegalStateException(MESSAGE_MARKER);
        }).when(repository).findState(any());

        String body = mvc.perform(SummaryJobFlowTest.retryRequest(jobId, "203.0.113.14"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("INTERNAL_ERROR").doesNotContain(jobId).doesNotContain(MESSAGE_MARKER);
        // 예외 메시지는 Spring의 ExceptionHandlerExceptionResolver DEBUG 로그("Resolved [...]")에 남는다.
        // 모든 API에 해당하는 기존 경로라 따로 다룬다 (W6 보고). 여기서는 작업 ID와 스택만 확인한다
        assertThat(output.getAll()).contains("/api/jobs/{id}/retry")
                .doesNotContain(jobId).doesNotContain("\tat ");
    }

    /** INTERNAL_ERROR 작업은 사용자의 다시 시도로 다시 실행된다. */
    @Test
    void internalErrorCanBeRetriedByUser(CapturedOutput output) throws Exception {
        respondWithResult();
        doAnswer(invocation -> {
            throw new DataIntegrityViolationException(SQL_MARKER);
        }).when(repository).saveDone(any(), anyList());
        String jobId = createJob("203.0.113.12");
        awaitStatus(repository, jobId, JobStatus.FAILED);
        reset(repository);

        mvc.perform(SummaryJobFlowTest.retryRequest(jobId, "203.0.113.13")).andExpect(status().isAccepted());

        awaitStatus(repository, jobId, JobStatus.DONE);
        assertThat(calls).hasValue(2);
        assertThat(output.getAll()).doesNotContain(jobId).doesNotContain(SQL_MARKER);
    }
}
