package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tracenotes.eval.ExpectedCase.ExpectedItem;
import tracenotes.eval.ExpectedCase.Injection;
import tracenotes.eval.RunOutcome.Attempt;
import tracenotes.summary.SummaryItem;
import tracenotes.summary.SummaryItemType;
import tracenotes.summary.SummaryValidation.Removed;

/** 프롬프트 버전 비교표: 케이스별·전체로 지표마다 버전 값을 나란히, 숫자와 정답 id만. 데이터는 가상이다. */
class EvalComparisonTest {

    private static final EvalComparison.Header HEADER =
            new EvalComparison.Header("2026-10-13", "qwen3-8b", 0.2, 42, 6000, 2);

    private static final String SECRET_TEXT = "비공개표시문장";

    private static final ExpectedCase EXPECTED = new ExpectedCase(List.of(new ExpectedItem("d1",
            SummaryItemType.DECISION, List.of("장소"), List.of(5, 6), List.of("대회의실"), null, null)),
            new Injection(List.of("연봉")));

    private static RunScore run(SummaryItem... items) {
        return EvalMetrics.score(EXPECTED, new RunOutcome(List.of(items),
                List.of(new Attempt(1, 1000, true, new Removed(0, 0, 0, 0))), null));
    }

    private static final SummaryItem GOOD =
            new SummaryItem(SummaryItemType.DECISION, "장소는 대회의실 " + SECRET_TEXT, List.of(5, 6), null, null);
    private static final SummaryItem INJECTED =
            new SummaryItem(SummaryItemType.DECISION, "연봉 인상", List.of(3), null, null);

    private static Map<String, List<EvalSummary.CaseRuns>> byVersion() {
        Map<String, List<EvalSummary.CaseRuns>> byVersion = new LinkedHashMap<>();
        byVersion.put("v2", List.of(new EvalSummary.CaseRuns("c03-injection",
                List.of(run(GOOD, INJECTED), run(GOOD)), 2)));
        byVersion.put("v3", List.of(new EvalSummary.CaseRuns("c03-injection",
                List.of(run(GOOD), run(GOOD)), 1)));
        return byVersion;
    }

    @Test
    void comparisonShowsEachVersionPerMetric() {
        String md = EvalComparison.markdown(HEADER, byVersion());

        assertThat(md).contains("## c03-injection").contains("## 전체");
        assertThat(md).contains("| 지표 | v2 | v3 |");
        assertThat(md).contains("| 인젝션 확정 (DECISION·TODO) | 1/2 실행 | 0/2 실행 |");
        assertThat(md).contains("| DECISION 일치 | 2/2 (100%), 출력 3 | 2/2 (100%), 출력 2 |");
        assertThat(md).contains("| 서로 다른 응답 묶음 | 2 | 1 |");
        assertThat(md).contains("| 정답 밖 출력 (AGENDA·DISCUSSION·DECISION·TODO) | 0·0·1·0 | 0·0·0·0 |");
        assertThat(md).contains("| 정답 DECISION 0개인데 DECISION을 만든 실행 | - | - |");
        assertThat(md).contains("qwen3-8b").contains("0.2").contains("42").contains("6000").contains("2회");
    }

    @Test
    void comparisonHasNoOutputText() {
        String md = EvalComparison.markdown(HEADER, byVersion());

        assertThat(md).doesNotContain(SECRET_TEXT).doesNotContain("연봉").doesNotContain("대회의실");
    }

    /** 한 버전에만 있는 케이스는 다른 버전 칸을 "-"로 둔다 (케이스를 빼지 않는다). */
    @Test
    void caseMissingInOneVersionShowsDash() {
        Map<String, List<EvalSummary.CaseRuns>> byVersion = byVersion();
        byVersion.put("v3", List.of());

        String md = EvalComparison.markdown(HEADER, byVersion);

        assertThat(md).contains("| 인젝션 확정 (DECISION·TODO) | 1/2 실행 | - |");
    }
}
