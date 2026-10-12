package tracenotes.summary;

/**
 * 정리 대기열 (ADR 0007). 작업을 저장하기 전에 자리를 먼저 잡아, 가득 차면 작업을 만들지 않는다.
 * 지금은 메모리 구현(MemorySummaryQueue)이고, 서버를 여러 대로 늘리면 DB 구현으로 바꾼다.
 */
public interface SummaryQueue {

    /**
     * 대기 자리를 하나 잡는다.
     *
     * @throws QueueFullException 기다리는 작업이 상한(app.summary.queue-capacity)만큼 있을 때
     */
    Reservation reserve();

    /** 잡은 자리. submit이나 cancel 중 하나를 한 번 부른다. 두 번째 호출부터는 무시한다. */
    interface Reservation {

        /** 저장한 작업을 대기열에 넣는다. */
        void submit(String jobId);

        /** 작업을 넣지 못했을 때(저장 실패 등) 자리를 돌려준다. */
        void cancel();
    }
}
