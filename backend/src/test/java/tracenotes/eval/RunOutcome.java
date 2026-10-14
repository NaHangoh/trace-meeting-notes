package tracenotes.eval;

import java.util.List;
import tracenotes.summary.SummaryItem;
import tracenotes.summary.SummaryValidation;

/**
 * 케이스 한 번 실행한 결과.
 *
 * @param items 덩어리 순서대로 이어 붙인 검증 후 항목 (제품 정리와 같은 결과)
 * @param attempts 응답을 받은 호출 (재시도 포함). 타임아웃·연결 실패처럼 응답이 없는 호출은 넣지 않고 failure로만 남긴다
 * @param failure 끝까지 못 간 이유 (형식 오류 재시도 초과, 타임아웃 등). 끝까지 갔으면 null
 */
record RunOutcome(List<SummaryItem> items, List<Attempt> attempts, String failure) {

    RunOutcome {
        items = List.copyOf(items);
        attempts = List.copyOf(attempts);
    }

    /**
     * 호출 하나.
     *
     * @param chunk 덩어리 번호 (1부터)
     * @param elapsedMs 걸린 시간
     * @param jsonValid 응답이 JSON 객체이고 items 배열이 있었는지 (검증기가 받아들였는지)
     * @param removed 검증기가 지운 개수. jsonValid가 아니면 null
     */
    record Attempt(int chunk, long elapsedMs, boolean jsonValid, SummaryValidation.Removed removed) {
    }
}
