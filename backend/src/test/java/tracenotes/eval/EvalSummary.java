package tracenotes.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import tracenotes.eval.RunScore.TypeCount;
import tracenotes.summary.SummaryItemType;

/**
 * 여러 번 실행한 지표를 합쳐 저장소에 남길 요약표(마크다운)를 만든다.
 * 비율은 실행마다 나눈 값의 평균이 아니라 분자·분모를 모두 더한 뒤 나눈다 (분모가 실행마다 달라도 왜곡되지 않게).
 * 회의 내용, 출력 text, 담당자·기한 값은 넣지 않고 숫자와 정답 id만 넣는다.
 */
final class EvalSummary {

    /**
     * @param date 측정일 (yyyy-MM-dd)
     * @param model 모델 이름 (설정값)
     * @param promptVersion 정리 프롬프트 버전
     * @param temperature 설정값
     * @param seed 설정값
     * @param chunkSize 덩어리 최대 글자 수
     * @param runs 케이스마다 실행 횟수
     */
    record Header(String date, String model, String promptVersion, double temperature, long seed, int chunkSize,
            int runs) {
    }

    /**
     * @param name 케이스 폴더 이름
     * @param scores 실행마다의 지표
     * @param distinctResponses 실행 사이에 서로 다른 응답 묶음 수 (1이면 매번 같은 응답)
     */
    record CaseRuns(String name, List<RunScore> scores, int distinctResponses) {
    }

    /**
     * 표의 한 행: 지표 이름과, 실행 묶음(케이스 하나 또는 전체)의 칸 값. 비교표(EvalComparison)도 같은 목록을 쓴다.
     *
     * @param name 행 이름
     * @param cell 실행 묶음 → 칸 문자열
     */
    record Metric(String name, Function<List<RunScore>, String> cell) {
    }

    static final List<Metric> METRICS = metrics();

    private EvalSummary() {
    }

    private static List<Metric> metrics() {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("완료한 실행", s -> count(s, RunScore::completed) + "/" + s.size()));
        metrics.add(new Metric("JSON 성공률",
                s -> ratio(sum(s, RunScore::validAttempts), sum(s, RunScore::attempts))));
        for (SummaryItemType type : SummaryItemType.values()) {
            metrics.add(new Metric(type + " 일치", s -> ratio(sum(s, r -> r.byType().get(type).matched()),
                    sum(s, r -> r.byType().get(type).expected())) + ", 출력 " + sum(s, r -> r.byType().get(type).output())));
        }
        metrics.add(new Metric("종류 무관 일치",
                s -> ratio(sum(s, RunScore::matchedAnyType), sum(s, EvalSummary::expectedCount))));
        metrics.add(new Metric("근거 겹침", s -> ratio(sum(s, RunScore::evidenceOverlap), sum(s, RunScore::evidencePairs))));
        metrics.add(new Metric("근거 정확 일치",
                s -> ratio(sum(s, RunScore::evidenceExact), sum(s, RunScore::evidencePairs))));
        metrics.add(new Metric("근거 번호 정밀도",
                s -> ratio(sum(s, RunScore::evidenceNumbersCorrect), sum(s, RunScore::evidenceNumbers))));
        metrics.add(new Metric("담당자 일치",
                s -> ratio(sum(s, RunScore::assigneeCorrect), sum(s, RunScore::assigneeTotal))));
        metrics.add(new Metric("담당자 정확 일치",
                s -> ratio(sum(s, RunScore::assigneeExact), sum(s, RunScore::assigneeTotal))));
        metrics.add(new Metric("기한 일치", s -> ratio(sum(s, RunScore::dueCorrect), sum(s, RunScore::dueTotal))));
        metrics.add(new Metric("기한 정확 일치", s -> ratio(sum(s, RunScore::dueExact), sum(s, RunScore::dueTotal))));
        metrics.add(new Metric("결정 값 포함",
                s -> ratio(sum(s, RunScore::decisionValueHit), sum(s, RunScore::decisionValueTotal))));
        metrics.add(new Metric("검증기 제거 (항목·근거·담당자·기한)", s -> sum(s, r -> r.removed().items()) + "·"
                + sum(s, r -> r.removed().evidence()) + "·" + sum(s, r -> r.removed().assignee()) + "·"
                + sum(s, r -> r.removed().due())));
        metrics.add(new Metric("인젝션 확정 (DECISION·TODO)", s -> injection(s, RunScore::injected)));
        metrics.add(new Metric("인젝션 표현 (AGENDA·DISCUSSION, 참고)", s -> injection(s, RunScore::injectedOtherType)));
        metrics.add(new Metric("덩어리당 시간 평균·최대", EvalSummary::chunkTime));
        metrics.add(new Metric("놓친 정답 (id×횟수)", EvalSummary::missing));
        return List.copyOf(metrics);
    }

    static String markdown(Header header, List<CaseRuns> cases) {
        StringBuilder md = new StringBuilder();
        md.append("# 정리 기준선: ").append(header.model()).append(" · summarize ").append(header.promptVersion())
                .append(" (").append(header.date()).append(")\n\n");
        md.append("| 설정 | 값 |\n|---|---|\n");
        md.append("| 모델 | ").append(header.model()).append(" |\n");
        md.append("| 프롬프트 | summarize.").append(header.promptVersion()).append(".md |\n");
        md.append("| temperature · seed | ").append(header.temperature()).append(" · ").append(header.seed())
                .append(" |\n");
        md.append("| 덩어리 크기 | ").append(header.chunkSize()).append(" |\n");
        md.append("| 케이스마다 실행 | ").append(header.runs()).append("회 |\n\n");

        List<List<RunScore>> columns = new ArrayList<>();
        md.append("| 지표 |");
        for (CaseRuns c : cases) {
            md.append(' ').append(c.name()).append(" |");
            columns.add(c.scores());
        }
        md.append(" 전체 |\n|---|");
        md.append("---|".repeat(cases.size() + 1)).append('\n');
        columns.add(cases.stream().flatMap(c -> c.scores().stream()).toList());

        for (Metric metric : METRICS) {
            row(md, metric.name(), columns, metric.cell());
        }
        md.append("| 서로 다른 응답 묶음 |");
        for (CaseRuns c : cases) {
            md.append(' ').append(c.distinctResponses()).append(" |");
        }
        md.append(" - |\n");
        return md.toString();
    }

    private static void row(StringBuilder md, String name, List<List<RunScore>> columns,
            Function<List<RunScore>, String> cell) {
        md.append("| ").append(name).append(" |");
        for (List<RunScore> scores : columns) {
            md.append(' ').append(cell.apply(scores)).append(" |");
        }
        md.append('\n');
    }

    private static int expectedCount(RunScore score) {
        return score.byType().values().stream().mapToInt(TypeCount::expected).sum();
    }

    private static int sum(List<RunScore> scores, ToIntFunction<RunScore> value) {
        return scores.stream().mapToInt(value).sum();
    }

    private static long count(List<RunScore> scores, Predicate<RunScore> test) {
        return scores.stream().filter(test).count();
    }

    private static String ratio(int part, int whole) {
        if (whole == 0) {
            return part + "/0 (-)";
        }
        return part + "/" + whole + " (" + Math.round(100.0 * part / whole) + "%)";
    }

    /** 인젝션 케이스 실행만 센다. 하나도 없으면 "-". */
    private static String injection(List<RunScore> scores, Function<RunScore, Boolean> value) {
        List<Boolean> values = scores.stream().map(value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return "-";
        }
        return values.stream().filter(Boolean::booleanValue).count() + "/" + values.size() + " 실행";
    }

    private static String chunkTime(List<RunScore> scores) {
        List<Long> ms = scores.stream().flatMap(s -> s.chunkMs().stream()).toList();
        if (ms.isEmpty()) {
            return "-";
        }
        double average = ms.stream().mapToLong(Long::longValue).average().orElseThrow() / 1000.0;
        double max = ms.stream().mapToLong(Long::longValue).max().orElseThrow() / 1000.0;
        return String.format(Locale.ROOT, "%.1f초·%.1f초", average, max);
    }

    /** 정답 id를 처음 나온 순서로, 놓친 횟수와 함께. 없으면 "-". */
    private static String missing(List<RunScore> scores) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        scores.forEach(s -> s.missing().forEach(id -> counts.merge(id, 1, Integer::sum)));
        if (counts.isEmpty()) {
            return "-";
        }
        return counts.entrySet().stream().map(e -> e.getKey() + "×" + e.getValue()).collect(Collectors.joining(", "));
    }
}
