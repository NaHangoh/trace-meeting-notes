package tracenotes.summary;

import java.util.List;

/**
 * 응답 검증 결과: 남긴 항목과, 검증이 지운 것의 종류별 개수.
 *
 * @param items 남긴 항목
 * @param removed 지운 개수
 */
public record SummaryValidation(List<SummaryItem> items, Removed removed) {

    public SummaryValidation {
        items = List.copyOf(items);
    }

    /**
     * 검증이 지운 것의 종류별 개수 (모델이 안 낸 것과 검증이 지운 것을 구분하려고 센다).
     * 버린 항목 안의 근거·담당자·기한은 따로 세지 않고, null로 오거나 필드가 없는 값도 세지 않는다.
     *
     * @param items 버린 항목 (알 수 없는 종류, 비었거나 긴 text, 객체 아님, 100개 초과)
     * @param evidence 남긴 항목에서 버린 근거 번호 (정수 아님, 덩어리 밖, 중복). 배열이 아닌 값으로 오면 1개로 센다
     * @param assignee 남긴 항목에서 값이 왔는데 비운 담당자 (TODO가 아닌 항목의 값 포함)
     * @param due 남긴 항목에서 값이 왔는데 비운 기한 (TODO가 아닌 항목의 값 포함)
     */
    public record Removed(int items, int evidence, int assignee, int due) {
    }

    /** 로그에 찍혀도 항목 내용이 나오지 않게 개수만 넣는다. */
    @Override
    public String toString() {
        return "SummaryValidation[items=" + items.size() + ", removed=" + removed + "]";
    }
}
