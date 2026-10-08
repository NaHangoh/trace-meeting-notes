package tracenotes.summary;

/**
 * 작업 상태와 실패 사유.
 *
 * @param failure FAILED일 때만 있고, 그 밖에는 null
 */
public record JobState(JobStatus status, SummaryFailure failure) {
}
