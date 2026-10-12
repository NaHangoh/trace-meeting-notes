package tracenotes.summary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tracenotes.summary.SummaryJobFlowTest.createRequest;
import static tracenotes.summary.SummaryJobFlowTest.retryRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tracenotes.minutes.JobRepository;
import tracenotes.minutes.Utterance;

/**
 * SPEC F2: 작업 조회는 IP당 분당 120회(app.rate-limit.status-per-minute), 넘으면 429 TOO_MANY_REQUESTS.
 * 작업 생성·다시 시도 제한(S4)과 서로 세지 않는다. 시계를 고정해 한 분 안에서 센다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RetryRateLimitTest.FixedClockConfig.class)
class StatusRateLimitTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JobRepository jobs;

    @Autowired
    SummaryRepository repository;

    private String failedJob() {
        String id = UUID.randomUUID().toString();
        jobs.insert(id, Instant.now(), List.of(new Utterance(1, "참석자", "내용")));
        repository.markFailed(id, SummaryFailure.TIMEOUT);
        return id;
    }

    private static MockHttpServletRequestBuilder statusRequest(String id, String ip) {
        return get("/api/jobs/" + id).with(r -> { r.setRemoteAddr(ip); return r; });
    }

    @Test
    void request121InSameMinuteReturns429() throws Exception {
        String ip = "192.0.2.90";
        String id = failedJob();
        for (int i = 0; i < 120; i++) {
            mvc.perform(statusRequest(id, ip)).andExpect(status().isOk());
        }

        mvc.perform(statusRequest(id, ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        // 다른 IP는 따로 센다
        mvc.perform(statusRequest(id, "192.0.2.91")).andExpect(status().isOk());
    }

    /** @GetMapping은 HEAD도 받는다. HEAD도 같은 제한기로 세어 조회 제한을 피할 수 없다. */
    @Test
    void headRequestsCountTowardStatusLimit() throws Exception {
        String ip = "192.0.2.95";
        String id = failedJob();
        for (int i = 0; i < 60; i++) {
            mvc.perform(head("/api/jobs/" + id).with(r -> { r.setRemoteAddr(ip); return r; })).andExpect(status().isOk());
            mvc.perform(statusRequest(id, ip)).andExpect(status().isOk());
        }

        mvc.perform(head("/api/jobs/" + id).with(r -> { r.setRemoteAddr(ip); return r; }))
                .andExpect(status().isTooManyRequests());
    }

    /** 없는 ID 조회도 센다. 404를 이용해 ID를 무제한으로 대입할 수 없다. */
    @Test
    void notFoundLookupsAlsoCount() throws Exception {
        String ip = "192.0.2.92";
        for (int i = 0; i < 120; i++) {
            mvc.perform(statusRequest(UUID.randomUUID().toString(), ip)).andExpect(status().isNotFound());
        }

        mvc.perform(statusRequest(UUID.randomUUID().toString(), ip)).andExpect(status().isTooManyRequests());
    }

    /** 조회가 상한에 걸려도 작업 생성·다시 시도는 따로 센다. */
    @Test
    void statusPollingDoesNotCountTowardJobLimit() throws Exception {
        String ip = "192.0.2.93";
        String id = failedJob();
        for (int i = 0; i < 121; i++) {
            mvc.perform(statusRequest(id, ip));
        }
        mvc.perform(statusRequest(id, ip)).andExpect(status().isTooManyRequests());

        mvc.perform(retryRequest(id, ip)).andExpect(status().isAccepted());
        for (int i = 0; i < 19; i++) {
            mvc.perform(createRequest(ip)).andExpect(status().isCreated());
        }
        mvc.perform(createRequest(ip)).andExpect(status().isTooManyRequests());
    }

    /** 작업 생성·다시 시도(POST)와 OPTIONS는 조회 제한에 세지 않는다. */
    @Test
    void jobCreationRetryAndOptionsDoNotCountTowardStatusLimit() throws Exception {
        String ip = "192.0.2.94";
        String id = failedJob();
        mvc.perform(retryRequest(id, ip)).andExpect(status().isAccepted());
        for (int i = 0; i < 19; i++) {
            mvc.perform(createRequest(ip)).andExpect(status().isCreated());
        }
        for (int i = 0; i < 130; i++) {
            mvc.perform(options("/api/jobs/" + id).with(r -> { r.setRemoteAddr(ip); return r; }));
        }

        for (int i = 0; i < 120; i++) {
            mvc.perform(statusRequest(id, ip)).andExpect(status().isOk());
        }
        mvc.perform(statusRequest(id, ip)).andExpect(status().isTooManyRequests());
    }
}
