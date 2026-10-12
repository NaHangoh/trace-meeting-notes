package tracenotes.summary;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 작업 조회 (SPEC F2, S1). 상태·실패 사유와, DONE이면 결과 항목을 돌려준다. 발언은 읽지 않는다.
 * 형식이 틀린 ID도 따로 거르지 않고 같은 조회를 거쳐, 없는 ID·삭제된 ID와 같은 경로로 404가 된다.
 * (작업 ID는 늘 UUID라 형식이 틀린 ID는 존재할 수 없지만, 경로를 하나로 두어 응답과 처리 시간을 맞춘다.)
 */
@Service
public class SummaryStatusService {

    private final SummaryRepository repository;

    public SummaryStatusService(SummaryRepository repository) {
        this.repository = repository;
    }

    /** @throws JobNotFoundException 작업이 없음 (형식이 틀린 ID, 없는 ID, 보관 기간이 지나 삭제된 ID) */
    public JobResult find(String jobId) {
        JobState state = repository.findState(jobId).orElseThrow(JobNotFoundException::new);
        // DONE과 결과는 한 트랜잭션으로 저장되고 DONE은 바뀌지 않으므로, DONE을 읽었으면 결과도 이미 있다.
        // 예외: 두 조회 사이에 보관 기간 삭제가 끼면 404 대신 항목 없는 DONE이 한 번 나갈 수 있다 (본문 노출은 없음)
        List<SummaryItem> items = state.status() == JobStatus.DONE ? repository.findItems(jobId) : null;
        return new JobResult(state, items);
    }
}
