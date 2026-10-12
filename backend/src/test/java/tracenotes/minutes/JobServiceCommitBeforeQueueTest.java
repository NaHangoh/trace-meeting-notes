package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tracenotes.summary.SummaryQueue;

/**
 * 작업은 커밋된 뒤에 대기열에 들어간다 (SPEC F2, ADR 0007). 커밋 전에 넣으면 작업자가 아직 보이지 않는 작업을 읽는다.
 * submit 시점에 다른 스레드(다른 커넥션)에서 작업 행과 발언이 보이는지 확인한다.
 */
@SpringBootTest
@Import(JobServiceCommitBeforeQueueTest.ObservingQueueConfig.class)
class JobServiceCommitBeforeQueueTest {

    @TestConfiguration
    static class ObservingQueueConfig {

        static final AtomicInteger visibleUtterances = new AtomicInteger(-1);

        /** submit할 때 별도 스레드에서 그 작업의 발언 수를 센다. 실제 작업자는 실행하지 않는다. */
        @Bean
        @Primary
        SummaryQueue observingQueue(JdbcTemplate jdbc) {
            return () -> new SummaryQueue.Reservation() {
                @Override
                public void submit(String jobId) {
                    int count = CompletableFuture.supplyAsync(() -> jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM job_utterance u JOIN job j ON j.id = u.job_id WHERE j.id = ?",
                                    Integer.class, jobId))
                            .orTimeout(10, TimeUnit.SECONDS)
                            .join();
                    visibleUtterances.set(count);
                }

                @Override
                public void cancel() {
                }
            };
        }
    }

    @Autowired
    JobService service;

    @Test
    void jobIsCommittedBeforeSubmit() {
        service.create("참석자: 첫 발언\n참석자: 둘째 발언");

        assertThat(ObservingQueueConfig.visibleUtterances).hasValue(2);
    }
}
