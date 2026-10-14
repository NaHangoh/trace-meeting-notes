package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.llm.LlmClient;
import tracenotes.llm.LlmConfig;
import tracenotes.llm.LlmProperties;
import tracenotes.llm.LlmProvider;
import tracenotes.minutes.Utterance;
import tracenotes.summary.EvalPromptBuilder;
import tracenotes.summary.PromptLoader;
import tracenotes.summary.ScriptChunker;
import tracenotes.summary.SummaryPromptBuilder;
import tracenotes.summary.SummaryProperties;
import tracenotes.summary.SummaryValidator;

/**
 * eval 기준선: eval/cases의 가상 회의를 실제 실행기로 정리해 지표를 낸다 (@Tag("llm"), `gradlew llmTest`로만 실행).
 * 설정(모델·temperature·seed·프롬프트 버전·덩어리 크기)과 키는 실행 설정(application.yml + 루트 .env)에서 Spring이 읽는다.
 *
 * <p>실행 변수 (비밀값 아님):
 * <ul>
 * <li>EVAL_OUT_DIR: 응답 원문과 요약표를 쓸 폴더. 실제 호출 때 필수, 절대 경로, 실제 경로가 저장소 밖이어야 한다</li>
 * <li>EVAL_RUNS: 케이스마다 실행 횟수 (기본 3)</li>
 * <li>EVAL_PROMPT_VERSIONS: 잴 정리 프롬프트 버전, 쉼표로 (예: v2,v3). 없으면 설정 버전. 둘 이상이면 comparison.md도 쓴다</li>
 * <li>EVAL_DRY_RUN=1: LLM을 부르지 않고 script.txt만 읽어 케이스·덩어리 수와 예상 호출 수를 출력한다</li>
 * </ul>
 * 표준 출력에는 개수·시간만 찍고 응답 내용은 찍지 않는다. 저장소에는 요약표(summary-&lt;버전&gt;.md 또는 comparison.md)만 옮긴다.
 */
@Tag("llm")
class EvalBaselineTest {

    @Configuration
    @EnableConfigurationProperties({LlmProperties.class, SummaryProperties.class})
    @Import(LlmConfig.class)
    static class Config {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    private static final int DEFAULT_RUNS = 3;

    /** gradle 작업은 backend/에서 돈다. */
    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();

    @Test
    void baseline() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JsonMapper json = context.getBean(JsonMapper.class);
                    LlmProperties llmProperties = context.getBean(LlmProperties.class);
                    SummaryProperties summaryProperties = context.getBean(SummaryProperties.class);
                    int runs = runs();
                    boolean dryRun = "1".equals(System.getenv("EVAL_DRY_RUN"));
                    // fixture 응답으로 기준선 숫자가 나오지 않게 한다 (.env·셸에 provider가 남아 있을 때)
                    assertThat(llmProperties.provider()).as("app.llm.provider").isNotEqualTo(LlmProvider.FIXTURE);

                    Path casesDir = REPO.resolve("eval/cases");
                    List<String> versions = EvalPromptVersions.parse(System.getenv("EVAL_PROMPT_VERSIONS"),
                            summaryProperties.promptVersion());
                    // 프롬프트 파일이 없으면 호출 전에 실패한다
                    Map<String, String> templates = new LinkedHashMap<>();
                    Map<String, EvalRunner> runners = new LinkedHashMap<>();
                    for (String version : versions) {
                        templates.put(version, new PromptLoader().summarizeTemplate(version));
                        runners.put(version, new EvalRunner(context.getBean(LlmClient.class),
                                new SummaryPromptBuilder(templates.get(version)),
                                new SummaryValidator(json), new ScriptChunker(summaryProperties.chunkSize()),
                                () -> System.nanoTime() / 1_000_000));
                    }

                    // 예상 호출 수는 발언만으로 센다 (정답을 쓰기 전에도 볼 수 있게)
                    Map<String, List<Utterance>> scripts = EvalCaseFiles.scripts(casesDir);
                    EvalRunner any = runners.values().iterator().next();
                    int chunks = scripts.values().stream().mapToInt(any::chunkCount).sum();
                    int calls = chunks * runs * versions.size();
                    System.out.println("EVAL_PLAN provider=" + llmProperties.provider() + " model="
                            + llmProperties.model() + " prompts=" + String.join(",", versions) + " temperature="
                            + llmProperties.temperature() + " seed=" + llmProperties.seed() + " cases="
                            + scripts.size() + " chunks=" + chunks + " runs=" + runs + " calls=" + calls
                            + " maxCallsWithRetries=" + (calls * (EvalRunner.MAX_RETRIES + 1)));
                    if (dryRun) {
                        return;
                    }
                    List<EvalCaseFiles.EvalCase> cases = EvalCaseFiles.load(casesDir, json);
                    Path out = EvalOutputDir.prepare(System.getenv("EVAL_OUT_DIR"), REPO);

                    Map<String, List<EvalSummary.CaseRuns>> byVersion = new LinkedHashMap<>();
                    versions.forEach(v -> byVersion.put(v, new ArrayList<>()));
                    for (EvalCaseFiles.EvalCase evalCase : cases) {
                        Map<String, List<RunScore>> scores = new LinkedHashMap<>();
                        Map<String, Set<List<String>>> distinct = new LinkedHashMap<>();
                        // 실행마다 버전을 번갈아 불러 서버 상태 차이가 한 버전에 몰리지 않게 한다.
                        // 구분자는 (케이스, 실행 번호)로 정해 같은 실행 번호의 버전들이 같은 발언 블록을 받는다
                        for (int r = 1; r <= runs; r++) {
                            for (String version : versions) {
                                EvalRunner.Run run = runners.get(version).run(evalCase.utterances(),
                                        EvalPromptBuilder.forRun(templates.get(version), evalCase.name(), r));
                                RunScore score = EvalMetrics.score(evalCase.expected(), run.outcome());
                                scores.computeIfAbsent(version, v -> new ArrayList<>()).add(score);
                                distinct.computeIfAbsent(version, v -> new HashSet<>()).add(run.responses());
                                writeRaw(json, out, version, evalCase.name(), r, run);
                                System.out.println("EVAL_RUN prompt=" + version + " case=" + evalCase.name() + " run="
                                        + r + " attempts=" + score.attempts() + " valid=" + score.validAttempts()
                                        + " failure=" + run.outcome().failure() + " chunkMs=" + score.chunkMs());
                            }
                        }
                        for (String version : versions) {
                            byVersion.get(version).add(new EvalSummary.CaseRuns(evalCase.name(), scores.get(version),
                                    distinct.get(version).size()));
                        }
                    }
                    String date = LocalDate.now().toString();
                    for (String version : versions) {
                        String summary = EvalSummary.markdown(new EvalSummary.Header(date, llmProperties.model(),
                                version, llmProperties.temperature(), llmProperties.seed(),
                                summaryProperties.chunkSize(), runs), byVersion.get(version));
                        Files.writeString(out.resolve("summary-" + version + ".md"), summary, StandardCharsets.UTF_8);
                    }
                    if (versions.size() > 1) {
                        String comparison = EvalComparison.markdown(new EvalComparison.Header(date,
                                llmProperties.model(), llmProperties.temperature(), llmProperties.seed(),
                                summaryProperties.chunkSize(), runs), byVersion);
                        Files.writeString(out.resolve("comparison.md"), comparison, StandardCharsets.UTF_8);
                    }
                    System.out.println("EVAL_DONE out=" + out);
                });
    }

    private static int runs() {
        String value = System.getenv("EVAL_RUNS");
        if (value == null || value.isBlank()) {
            return DEFAULT_RUNS;
        }
        int runs = Integer.parseInt(value.strip());
        if (runs < 1) {
            throw new IllegalArgumentException("EVAL_RUNS must be at least 1");
        }
        return runs;
    }

    private static void writeRaw(JsonMapper json, Path out, String version, String caseName, int run,
            EvalRunner.Run result) throws IOException {
        Path dir = out.resolve("raw").resolve(version).resolve(caseName);
        Files.createDirectories(dir);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("prompt", version);
        raw.put("case", caseName);
        raw.put("run", run);
        raw.put("failure", result.outcome().failure());
        raw.put("attempts", result.outcome().attempts());
        raw.put("responses", result.responses());
        raw.put("items", result.outcome().items());
        Files.writeString(dir.resolve("run" + run + ".json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(raw), StandardCharsets.UTF_8);
    }
}
