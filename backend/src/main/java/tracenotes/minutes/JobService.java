package tracenotes.minutes;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tracenotes.summary.SummaryQueue;

/**
 * 작업 생성 (SPEC F1, F2, S1). 입력을 다시 검사·분할하고, 대기열 자리를 먼저 잡은 뒤 저장해 정리 대기열에 넣는다.
 * 대기열이 가득 차면 작업을 만들지 않는다 (ADR 0007).
 */
@Service
public class JobService {

    private final ScriptParser parser;
    private final JobRepository repository;
    private final SummaryQueue queue;
    private final Clock clock;

    public JobService(ScriptParser parser, JobRepository repository, SummaryQueue queue, Clock clock) {
        this.parser = parser;
        this.repository = repository;
        this.queue = queue;
        this.clock = clock;
    }

    /**
     * 작업 ID는 UUID v4(SecureRandom 기반, UUID.randomUUID). 작업 ID가 곧 열람 권한이라 로그에 남기지 않는다.
     *
     * @throws tracenotes.summary.QueueFullException 대기열이 가득 참 (작업을 만들지 않음)
     */
    public String create(String script) {
        List<Utterance> utterances = parser.parse(script);
        SummaryQueue.Reservation reservation = queue.reserve();
        String jobId = UUID.randomUUID().toString();
        try {
            // 작업과 발언은 한 트랜잭션. 커밋된 뒤에 대기열에 넣어야 작업자가 발언을 읽을 수 있다
            repository.insert(jobId, clock.instant(), utterances);
        } catch (RuntimeException e) {
            reservation.cancel();
            throw e;
        }
        reservation.submit(jobId);
        return jobId;
    }
}
