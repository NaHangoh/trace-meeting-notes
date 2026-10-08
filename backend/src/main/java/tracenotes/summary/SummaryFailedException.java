package tracenotes.summary;

public class SummaryFailedException extends RuntimeException {

    private final SummaryFailure reason;
    private final int retries;

    public SummaryFailedException(SummaryFailure reason, int retries) {
        super("summary failed: " + reason);
        this.reason = reason;
        this.retries = retries;
    }

    public SummaryFailure reason() {
        return reason;
    }

    public int retries() {
        return retries;
    }
}
