package tracenotes.summary;

import java.util.List;

/**
 * 작업 조회 결과 (SPEC F2): 상태·실패 사유와, DONE일 때만 결과 항목. 발언 본문은 담지 않는다.
 *
 * @param items DONE이면 저장 순서대로의 항목, 그 밖에는 null
 */
public record JobResult(JobState state, List<SummaryItem> items) {

    public JobResult {
        items = items == null ? null : List.copyOf(items);
    }

    /** 로그에 찍혀도 항목 내용이 나오지 않게 개수만 남긴다. */
    @Override
    public String toString() {
        return "JobResult[status=" + state.status() + ", failure=" + state.failure()
                + ", items=" + (items == null ? "none" : items.size()) + "]";
    }
}
