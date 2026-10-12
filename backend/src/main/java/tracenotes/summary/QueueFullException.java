package tracenotes.summary;

/** 대기열이 가득 차 작업을 받을 수 없음 (SPEC F2 동시 작업, 429 QUEUE_FULL). */
public class QueueFullException extends RuntimeException {

    public QueueFullException() {
        super("summary queue is full");
    }
}
