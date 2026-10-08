package tracenotes.summary;

import java.util.List;

/**
 * 검증을 거친 회의록 항목 하나 (SPEC F2).
 *
 * @param type 항목 종류
 * @param text 내용 (500자 이하)
 * @param evidence 덩어리 안에 실제로 있는 근거 발언 번호. 비어 있으면 "근거 없음"
 * @param assignee 담당자 (TODO만, 근거 발언에 있는 표현만, 없으면 null)
 * @param due 기한 (TODO만, 근거 발언에 있는 표현만, 없으면 null)
 */
public record SummaryItem(SummaryItemType type, String text, List<Integer> evidence, String assignee, String due) {

    public SummaryItem {
        evidence = List.copyOf(evidence);
    }

    /** 근거 발언이 하나 이상 남았는지. false면 화면에 "근거 없음"으로 표시한다. */
    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }

    /** 로그에 찍혀도 회의 내용·담당자가 나오지 않게 한다. */
    @Override
    public String toString() {
        return "SummaryItem[type=" + type + ", textLength=" + text.length() + ", evidence=" + evidence
                + ", assignee=" + (assignee == null ? "none" : "set") + ", due=" + (due == null ? "none" : "set") + "]";
    }
}
