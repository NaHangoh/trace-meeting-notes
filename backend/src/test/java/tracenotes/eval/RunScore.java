package tracenotes.eval;

import java.util.List;
import java.util.Map;
import tracenotes.summary.SummaryItemType;
import tracenotes.summary.SummaryValidation;

/**
 * 케이스 한 번 실행의 지표 (분자·분모를 그대로 둔다. 여러 번 실행한 평균은 합친 뒤 나눈다).
 *
 * @param attempts 호출 수 (재시도 포함)
 * @param validAttempts JSON 형식이 맞은 호출 수
 * @param byType 종류별 정답 수·일치 수·출력 수
 * @param matchedAnyType 종류를 보지 않고 찾은 정답 수 (종류만 틀린 항목을 가려내려고)
 * @param missing 종류까지 맞춰 찾지 못한 정답 id
 * @param evidencePairs 종류까지 일치한 쌍 수 (근거·담당자 지표의 대상)
 * @param evidenceOverlap 근거가 정답과 하나 이상 겹친 쌍
 * @param evidenceExact 근거가 정답과 같은 집합인 쌍
 * @param evidenceNumbers 일치한 쌍의 출력 근거 번호 수
 * @param evidenceNumbersCorrect 그중 정답에 있는 번호 수
 * @param assigneeTotal 일치한 TODO 쌍 수
 * @param assigneeCorrect 담당자가 정답과 일치한 쌍: 둘 다 null이거나, 공백 정규화 후 한쪽이 다른 쪽에 들어 있음 (조사·호칭 차이)
 * @param assigneeExact 그중 공백 정규화 후 완전히 같은 쌍 (둘 다 null 포함)
 * @param dueTotal 일치한 TODO 쌍 수
 * @param dueCorrect 기한이 정답과 일치한 쌍 (담당자와 같은 기준)
 * @param dueExact 그중 완전히 같은 쌍
 * @param decisionValueTotal values가 있는 정답 DECISION 수
 * @param decisionValueHit 그중 종류까지 일치하고 text에 values가 모두 들어 있는 수
 * @param removed 검증기가 지운 개수 합 (JSON이 맞은 호출만)
 * @param injected 인젝션 표현이 DECISION·TODO에 들어갔는지. 인젝션 케이스가 아니면 null
 * @param injectedOtherType 인젝션 표현이 AGENDA·DISCUSSION에 들어갔는지 (참고). 인젝션 케이스가 아니면 null
 * @param chunkMs 덩어리마다 걸린 시간 (재시도 포함 합)
 * @param completed 모든 덩어리를 끝냈는지
 */
record RunScore(int attempts, int validAttempts, Map<SummaryItemType, TypeCount> byType, int matchedAnyType,
        List<String> missing, int evidencePairs, int evidenceOverlap, int evidenceExact, int evidenceNumbers,
        int evidenceNumbersCorrect, int assigneeTotal, int assigneeCorrect, int assigneeExact, int dueTotal,
        int dueCorrect, int dueExact,
        int decisionValueTotal, int decisionValueHit, SummaryValidation.Removed removed, Boolean injected,
        Boolean injectedOtherType, List<Long> chunkMs, boolean completed) {

    /**
     * @param expected 정답 수
     * @param matched 종류까지 일치한 정답 수 (출력과 일대일)
     * @param output 출력 수
     */
    record TypeCount(int expected, int matched, int output) {
    }
}
