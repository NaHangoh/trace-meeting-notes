package tracenotes.summary;

import org.springframework.stereotype.Service;

/**
 * 다시 시도 (SPEC F2): FAILED 작업만 같은 발언으로 다시 대기열에 넣는다. 대기열 상한을 적용받는다.
 * 요청 수 제한(S4)은 작업 생성과 같은 제한기로 센다 (RateLimitConfig).
 */
@Service
public class SummaryRetryService {

    private final SummaryRepository repository;
    private final SummaryQueue queue;

    public SummaryRetryService(SummaryRepository repository, SummaryQueue queue) {
        this.repository = repository;
        this.queue = queue;
    }

    /**
     * @throws JobNotFoundException 작업이 없음
     * @throws JobNotRetryableException FAILED가 아님
     * @throws QueueFullException 대기열이 가득 참 (작업 상태는 그대로)
     */
    public void retry(String jobId) {
        JobState state = repository.findState(jobId).orElseThrow(JobNotFoundException::new);
        if (state.status() != JobStatus.FAILED) {
            throw new JobNotRetryableException();
        }
        SummaryQueue.Reservation reservation = queue.reserve();
        try {
            // 확인한 뒤 다른 요청이 먼저 다시 시도했으면 조건부 UPDATE가 0행이다
            if (!repository.requeue(jobId)) {
                throw repository.findState(jobId).isPresent() ? new JobNotRetryableException() : new JobNotFoundException();
            }
        } catch (RuntimeException e) {
            reservation.cancel();
            throw e;
        }
        reservation.submit(jobId);
    }
}
