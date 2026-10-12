package tracenotes.summary;

import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracenotes.minutes.JobRepository;

/**
 * 작업자가 실행하는 정리 작업 하나 (SPEC F2): QUEUED → RUNNING → DONE 또는 FAILED.
 * 예상 밖 예외는 FAILED(INTERNAL_ERROR)로 남기고 자동으로 다시 실행하지 않는다.
 * 예외 메시지에는 SQL과 작업 ID가 들어갈 수 있으므로 로그에는 실패 종류와 예외 클래스 이름만 남긴다 (스택 없음).
 */
public class SummaryJobRunner implements Consumer<String> {

    private static final Logger log = LoggerFactory.getLogger(SummaryJobRunner.class);

    private final JobRepository jobs;
    private final SummaryRepository repository;
    private final SummarizeService summarizer;

    public SummaryJobRunner(JobRepository jobs, SummaryRepository repository, SummarizeService summarizer) {
        this.jobs = jobs;
        this.repository = repository;
        this.summarizer = summarizer;
    }

    @Override
    public void accept(String jobId) {
        try {
            run(jobId);
        } catch (RuntimeException e) {
            // 상태를 바꾸는 DB 호출 자체가 실패함. 작업은 RUNNING에 남고 다음 시작 때 SERVER_RESTART가 된다
            log.error("summary job state not saved ({})", e.getClass().getName());
        }
    }

    private void run(String jobId) {
        // 이미 다른 상태(삭제됨, 재시작 처리됨)면 실행하지 않는다
        if (!repository.markRunning(jobId)) {
            return;
        }
        SummaryFailure failure;
        try {
            List<SummaryItem> items = summarizer.summarize(jobs.findUtterances(jobId));
            withoutInterrupt(() -> repository.saveDone(jobId, items));
            return;
        } catch (SummaryFailedException e) {
            // SummarizeService가 실패 종류와 재시도 횟수를 이미 로그에 남겼다
            failure = e.reason();
        } catch (RuntimeException e) {
            failure = SummaryFailure.INTERNAL_ERROR;
            log.error("summary failed: {} ({})", failure, e.getClass().getName());
        }
        SummaryFailure reason = failure;
        withoutInterrupt(() -> repository.markFailed(jobId, reason));
    }

    /**
     * 서버 종료로 중단 표시가 켜진 상태에서도 마지막 상태는 DB에 남긴다. 중단 표시를 잠시 내렸다가 되돌린다.
     */
    private static void withoutInterrupt(Runnable write) {
        boolean interrupted = Thread.interrupted();
        try {
            write.run();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
