package tracenotes.eval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 프롬프트 버전 비교표 (마크다운). 지표 계산은 EvalSummary와 같고, 숫자와 정답 id만 넣는다. */
final class EvalComparison {

    /**
     * @param date 측정일
     * @param model 모델 이름
     * @param temperature 설정값
     * @param seed 설정값
     * @param chunkSize 덩어리 최대 글자 수
     * @param runs 케이스·버전마다 실행 횟수
     */
    record Header(String date, String model, double temperature, long seed, int chunkSize, int runs) {
    }

    private EvalComparison() {
    }

    /** 케이스는 버전들에 나온 순서대로 모두 싣는다. 한 버전에 없는 케이스는 그 칸을 "-"로 둔다. */
    static String markdown(Header header, Map<String, List<EvalSummary.CaseRuns>> byVersion) {
        List<String> versions = List.copyOf(byVersion.keySet());
        StringBuilder md = new StringBuilder();
        md.append("# 정리 프롬프트 비교: ").append(String.join(" · ", versions)).append(" (").append(header.model())
                .append(", ").append(header.date()).append(")\n\n");
        md.append("| 설정 | 값 |\n|---|---|\n");
        md.append("| 모델 | ").append(header.model()).append(" |\n");
        md.append("| 프롬프트 | ").append(versions.stream().map(v -> "summarize." + v + ".md")
                .collect(Collectors.joining(", "))).append(" |\n");
        md.append("| temperature · seed | ").append(header.temperature()).append(" · ").append(header.seed())
                .append(" |\n");
        md.append("| 덩어리 크기 | ").append(header.chunkSize()).append(" |\n");
        md.append("| 케이스·버전마다 실행 | ").append(header.runs()).append("회 |\n");

        Set<String> caseNames = new LinkedHashSet<>();
        byVersion.values().forEach(cases -> cases.forEach(c -> caseNames.add(c.name())));
        for (String caseName : caseNames) {
            List<EvalSummary.CaseRuns> perVersion = versions.stream()
                    .map(v -> byVersion.get(v).stream().filter(c -> c.name().equals(caseName)).findFirst().orElse(null))
                    .toList();
            table(md, caseName, versions, perVersion.stream().map(c -> c == null ? null : c.scores()).toList());
            md.append("| 서로 다른 응답 묶음 |");
            perVersion.forEach(c -> md.append(' ').append(c == null ? "-" : String.valueOf(c.distinctResponses()))
                    .append(" |"));
            md.append('\n');
        }
        table(md, "전체", versions, versions.stream()
                .map(v -> byVersion.get(v).stream().flatMap(c -> c.scores().stream()).toList()).toList());
        return md.toString();
    }

    /** scores 원소가 null이면 그 버전에는 이 케이스가 없다. */
    private static void table(StringBuilder md, String title, List<String> versions, List<List<RunScore>> scores) {
        md.append("\n## ").append(title).append("\n\n| 지표 |");
        versions.forEach(v -> md.append(' ').append(v).append(" |"));
        md.append("\n|---|").append("---|".repeat(versions.size())).append('\n');
        for (EvalSummary.Metric metric : EvalSummary.METRICS) {
            md.append("| ").append(metric.name()).append(" |");
            scores.forEach(s -> md.append(' ').append(s == null ? "-" : metric.cell().apply(s)).append(" |"));
            md.append('\n');
        }
    }
}
