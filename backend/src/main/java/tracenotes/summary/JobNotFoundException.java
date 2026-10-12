package tracenotes.summary;

/** 작업이 없음. 형식이 틀린 ID, 없는 ID, 보관 기간이 지나 삭제된 ID를 구분하지 않는다 (같은 404). */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException() {
        super("job not found");
    }
}
