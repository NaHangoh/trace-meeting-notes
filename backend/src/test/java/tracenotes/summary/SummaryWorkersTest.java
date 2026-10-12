package tracenotes.summary;

import static tracenotes.summary.SummaryJobFlowTest.awaitStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tracenotes.llm.FixtureLlmClient;
import tracenotes.llm.LlmClient;
import tracenotes.minutes.JobService;

/** ADR 0007 확장 경로: 작업자 수는 설정값(app.summary.workers)이다. */
@SpringBootTest(properties = "app.summary.workers=2")
class SummaryWorkersTest {

    @Autowired
    LlmClient llm;

    @Autowired
    JobService jobService;

    @Autowired
    SummaryRepository repository;

    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> created = new ArrayList<>();

    @AfterEach
    void drain() throws Exception {
        release.countDown();
        for (String id : created) {
            awaitStatus(repository, id, JobStatus.DONE, JobStatus.FAILED);
        }
        ((FixtureLlmClient) llm).reset();
    }

    @Test
    void workersFollowConfig() throws Exception {
        ((FixtureLlmClient) llm).respondWith(request -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return FixtureLlmClient.EMPTY_RESULT;
        });

        created.add(jobService.create("참석자: 첫 작업"));
        created.add(jobService.create("참석자: 둘째 작업"));

        // 작업자 2개: 두 작업이 동시에 실행 중이 된다
        awaitStatus(repository, created.get(0), JobStatus.RUNNING);
        awaitStatus(repository, created.get(1), JobStatus.RUNNING);
    }
}
