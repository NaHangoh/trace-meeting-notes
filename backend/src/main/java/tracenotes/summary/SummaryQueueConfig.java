package tracenotes.summary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tracenotes.minutes.JobRepository;

/** 정리 대기열과 작업자 구성 (ADR 0007). 작업자 수와 대기열 상한은 app.summary.* 설정값. */
@Configuration
public class SummaryQueueConfig {

    /** 대기열보다 먼저 만들어져, 새 작업을 받기 전에 지난 실행의 미완료 작업을 정리한다. */
    @Bean
    SummaryStartupRecovery summaryStartupRecovery(SummaryRepository repository) {
        return new SummaryStartupRecovery(repository);
    }

    /** 종료 시 실행 중 작업을 중단한다. 저장소·DB보다 먼저 닫혀(의존하는 빈이 먼저 닫힘) 마지막 상태를 남길 수 있다. */
    @Bean(destroyMethod = "shutdown")
    MemorySummaryQueue summaryQueue(SummaryProperties properties, SummaryStartupRecovery recovery, JobRepository jobs,
            SummaryRepository repository, SummarizeService summarizer) {
        return new MemorySummaryQueue(properties.workers(), properties.queueCapacity(),
                new SummaryJobRunner(jobs, repository, summarizer));
    }
}
