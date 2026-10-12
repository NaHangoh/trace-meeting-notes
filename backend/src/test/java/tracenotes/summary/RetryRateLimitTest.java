package tracenotes.summary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tracenotes.summary.SummaryJobFlowTest.awaitStatus;
import static tracenotes.summary.SummaryJobFlowTest.createRequest;
import static tracenotes.summary.SummaryJobFlowTest.retryRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tracenotes.minutes.JobRepository;
import tracenotes.minutes.Utterance;

/** SPEC F2: 다시 시도도 작업 생성과 같은 요청 수 제한(S4, IP당 시간당 20회)을 받는다. 같은 제한기를 함께 센다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RetryRateLimitTest.FixedClockConfig.class)
class RetryRateLimitTest {

    /** 테스트 중 시간 창이 바뀌지 않게 시계를 고정한다 (minutes.PreviewRateLimitTest와 같은 시각). */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-08T09:00:30Z"), ZoneOffset.UTC);
        }
    }

    private static final String IP = "192.0.2.80";

    @Autowired
    MockMvc mvc;

    @Autowired
    JobRepository jobs;

    @Autowired
    SummaryRepository repository;

    @Test
    void retryCountsTowardJobLimit() throws Exception {
        String failed = UUID.randomUUID().toString();
        jobs.insert(failed, Instant.now(), List.of(new Utterance(1, "참석자", "내용")));
        repository.markFailed(failed, SummaryFailure.TIMEOUT);

        for (int i = 0; i < 19; i++) {
            mvc.perform(createRequest(IP)).andExpect(status().isCreated());
        }
        mvc.perform(retryRequest(failed, IP)).andExpect(status().isAccepted());
        awaitStatus(repository, failed, JobStatus.DONE);

        mvc.perform(createRequest(IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        mvc.perform(retryRequest(failed, IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    /** 작업 생성·다시 시도 제한기는 POST만 센다. 같은 경로의 GET·OPTIONS(CORS 사전 요청)는 세지 않는다. */
    @Test
    void onlyPostCountsTowardJobLimit() throws Exception {
        String ip = "192.0.2.81";
        String path = "/api/jobs/" + UUID.randomUUID() + "/retry";
        for (int i = 0; i < 25; i++) {
            mvc.perform(get(path).with(remoteAddr(ip)));
            mvc.perform(options(path).with(remoteAddr(ip)));
            mvc.perform(get("/api/jobs").with(remoteAddr(ip)));
            mvc.perform(options("/api/jobs").with(remoteAddr(ip)));
        }

        for (int i = 0; i < 20; i++) {
            mvc.perform(createRequest(ip)).andExpect(status().isCreated());
        }
        mvc.perform(createRequest(ip)).andExpect(status().isTooManyRequests());
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return r -> { r.setRemoteAddr(ip); return r; };
    }
}
