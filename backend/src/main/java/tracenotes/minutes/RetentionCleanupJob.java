package tracenotes.minutes;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보관 기간이 지난 작업과 발언을 지운다 (SPEC S2). 로그에는 지운 개수만 남긴다. */
@Component
public class RetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupJob.class);

    private final JobRepository repository;
    private final RetentionProperties properties;
    private final Clock clock;

    public RetentionCleanupJob(JobRepository repository, RetentionProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.retention-cleanup-interval}",
            fixedDelayString = "${app.retention-cleanup-interval}")
    public void deleteExpired() {
        int deleted = repository.deleteCreatedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("deleted {} expired jobs", deleted);
        }
    }
}
