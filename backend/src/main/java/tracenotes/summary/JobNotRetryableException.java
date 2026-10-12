package tracenotes.summary;

/** FAILED가 아닌 작업을 다시 시도함 (409). */
public class JobNotRetryableException extends RuntimeException {

    public JobNotRetryableException() {
        super("only failed jobs can be retried");
    }
}
