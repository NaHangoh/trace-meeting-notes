package tracenotes.summary;

/** 정리 작업 상태 (SPEC F2). QUEUED → RUNNING → DONE 또는 FAILED. */
public enum JobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED
}
