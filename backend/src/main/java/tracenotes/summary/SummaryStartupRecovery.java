package tracenotes.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서버 시작 시 QUEUED·RUNNING이던 작업을 FAILED(SERVER_RESTART)로 바꾼다 (SPEC F2, ADR 0007).
 * 메모리 대기열은 재시작하면 비므로 그 작업들은 다시 실행되지 않는다. 사용자가 다시 시도할 수 있다.
 * 대기열이 이 객체를 받아 만들어지므로 새 작업을 받기 전에 실행된다. 로그에는 개수만 남긴다.
 */
public class SummaryStartupRecovery {

    private static final Logger log = LoggerFactory.getLogger(SummaryStartupRecovery.class);

    public SummaryStartupRecovery(SummaryRepository repository) {
        int failed = repository.failUnfinished(SummaryFailure.SERVER_RESTART);
        if (failed > 0) {
            log.info("marked {} unfinished summary jobs as {}", failed, SummaryFailure.SERVER_RESTART);
        }
    }
}
