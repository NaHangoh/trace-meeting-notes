package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.eval.RunOutcome.Attempt;
import tracenotes.llm.LlmCallException;
import tracenotes.llm.LlmClient;
import tracenotes.llm.LlmFailureKind;
import tracenotes.llm.LlmRequest;
import tracenotes.minutes.Utterance;
import tracenotes.summary.EvalPromptBuilder;
import tracenotes.summary.ScriptChunker;
import tracenotes.summary.SummaryItemType;
import tracenotes.summary.SummaryPromptBuilder;
import tracenotes.summary.SummaryValidation.Removed;
import tracenotes.summary.SummaryValidator;

/** eval 실행기: 가짜 LLM(정해 둔 응답)으로 재시도·시간·지운 개수 기록을 확인한다. 데이터는 가상이다. */
class EvalRunnerTest {

    private static final String TEMPLATE = "발언을 정리하라. 블록: {{DELIMITER}}";

    private static final List<Utterance> SCRIPT = List.of(
            new Utterance(1, "김민수", "출시일은 11월 둘째 주로 확정합니다."),
            new Utterance(2, "이영희", "보고서는 제가 금요일까지 쓰겠습니다."));

    /** 줄이 60자쯤인 발언 둘. 덩어리 크기 100이면 덩어리 둘. */
    private static final List<Utterance> LONG_SCRIPT = List.of(
            new Utterance(1, "김민수", "출시일은 11월 둘째 주로 확정합니다. " + "가".repeat(40)),
            new Utterance(2, "이영희", "보고서는 제가 금요일까지 쓰겠습니다. " + "나".repeat(40)));

    private static final String VALID ="{\"items\":[{\"type\":\"DECISION\",\"text\":\"출시일 11월 둘째 주\","
            + "\"evidence\":[1,9],\"assignee\":null,\"due\":null}]}";

    /** 호출마다 시계가 1,000ms씩 간다. */
    private final AtomicLong clock = new AtomicLong();

    private EvalRunner runner(Deque<Supplier<String>> responses, int chunkSize) {
        LlmClient llm = request -> {
            clock.addAndGet(1000);
            return responses.removeFirst().get();
        };
        return new EvalRunner(llm, new SummaryPromptBuilder(TEMPLATE),
                new SummaryValidator(JsonMapper.builder().build()), new ScriptChunker(chunkSize), clock::get);
    }

    private static Deque<Supplier<String>> responses(List<Supplier<String>> list) {
        return new ArrayDeque<>(list);
    }

    @Test
    void validResponseKeepsValidatedItemsTimeAndRemovedCounts() {
        EvalRunner.Run run = runner(responses(List.of(() -> VALID)), 6000).run(SCRIPT);

        assertThat(run.outcome().failure()).isNull();
        assertThat(run.outcome().items()).hasSize(1);
        assertThat(run.outcome().items().getFirst().type()).isEqualTo(SummaryItemType.DECISION);
        // 덩어리 밖 번호 9는 검증기가 지운다
        assertThat(run.outcome().items().getFirst().evidence()).containsExactly(1);
        assertThat(run.outcome().attempts()).containsExactly(new Attempt(1, 1000, true, new Removed(0, 1, 0, 0)));
        assertThat(run.responses()).containsExactly(VALID);
    }

    @Test
    void invalidJsonIsRetriedAndEachAttemptIsRecorded() {
        EvalRunner.Run run = runner(responses(List.of(() -> "not json", () -> "{\"x\":1}", () -> VALID)), 6000)
                .run(SCRIPT);

        assertThat(run.outcome().failure()).isNull();
        assertThat(run.outcome().attempts()).extracting(Attempt::jsonValid).containsExactly(false, false, true);
        assertThat(run.outcome().attempts()).extracting(Attempt::removed).containsExactly(null, null,
                new Removed(0, 1, 0, 0));
        assertThat(run.responses()).containsExactly("not json", "{\"x\":1}", VALID);
    }

    @Test
    void malformedResponseKindIsRetriedLikeInvalidJson() {
        EvalRunner.Run run = runner(responses(List.of(() -> {
            throw new LlmCallException(LlmFailureKind.MALFORMED_RESPONSE, "no content");
        }, () -> VALID)), 6000).run(SCRIPT);

        assertThat(run.outcome().failure()).isNull();
        assertThat(run.outcome().attempts()).extracting(Attempt::jsonValid).containsExactly(false, true);
    }

    @Test
    void threeInvalidResponsesFailTheRun() {
        EvalRunner.Run run = runner(responses(List.of(() -> "a", () -> "b", () -> "c")), 6000).run(SCRIPT);

        assertThat(run.outcome().failure()).isEqualTo("INVALID_OUTPUT");
        assertThat(run.outcome().attempts()).hasSize(3);
        assertThat(run.outcome().items()).isEmpty();
    }

    /** 응답이 없는 실패는 SummarizeService처럼 다시 부르지 않고 멈춘다. */
    @ParameterizedTest
    @EnumSource(value = LlmFailureKind.class, names = "MALFORMED_RESPONSE", mode = EnumSource.Mode.EXCLUDE)
    void noResponseFailureStopsWithoutRetryAndIsNotAnAttempt(LlmFailureKind kind) {
        Deque<Supplier<String>> queue = responses(List.of(() -> {
            throw new LlmCallException(kind, "failed");
        }, () -> VALID));

        EvalRunner.Run run = runner(queue, 6000).run(SCRIPT);

        assertThat(run.outcome().failure()).isEqualTo(kind.name());
        assertThat(run.outcome().attempts()).isEmpty();
        assertThat(queue).hasSize(1);
    }

    /** 첫 덩어리가 형식 오류 재시도를 넘기면 남은 덩어리는 부르지 않는다. */
    @Test
    void failedChunkStopsRemainingChunks() {
        Deque<Supplier<String>> queue = responses(List.of(() -> "a", () -> "b", () -> "c", () -> VALID));

        EvalRunner.Run run = runner(queue, 100).run(LONG_SCRIPT);

        assertThat(run.outcome().failure()).isEqualTo("INVALID_OUTPUT");
        assertThat(run.outcome().attempts()).extracting(Attempt::chunk).containsExactly(1, 1, 1);
        assertThat(queue).hasSize(1);
    }

    /** 실행마다 넘긴 요청 생성기를 쓴다. 같은 (케이스, 실행 번호)면 두 프롬프트 버전이 같은 발언 블록을 받는다. */
    @Test
    void runUsesGivenBuilderSoSameRunGetsSameBlockAcrossVersions() {
        List<LlmRequest> requests = new ArrayList<>();
        LlmClient llm = request -> {
            requests.add(request);
            return VALID;
        };
        EvalRunner runner = new EvalRunner(llm, new SummaryPromptBuilder(TEMPLATE),
                new SummaryValidator(JsonMapper.builder().build()), new ScriptChunker(6000), clock::get);

        runner.run(SCRIPT, EvalPromptBuilder.forRun(TEMPLATE, "c01-launch-schedule", 1));
        runner.run(SCRIPT, EvalPromptBuilder.forRun("다른 버전 지시. {{DELIMITER}}", "c01-launch-schedule", 1));
        runner.run(SCRIPT, EvalPromptBuilder.forRun(TEMPLATE, "c01-launch-schedule", 2));

        assertThat(requests).hasSize(3);
        assertThat(requests.get(1).userContent()).isEqualTo(requests.get(0).userContent());
        assertThat(requests.get(1).systemPrompt()).startsWith("다른 버전 지시.");
        assertThat(requests.get(2).userContent()).isNotEqualTo(requests.get(0).userContent());
    }

    /** 덩어리마다 따로 부르고 덩어리 번호를 남긴다. 줄이 60자쯤인 발언 둘이라 덩어리 크기 100이면 하나씩. */
    @Test
    void eachChunkIsCalledAndNumbered() {
        String second = "{\"items\":[]}";
        EvalRunner runner = runner(responses(List.of(() -> VALID, () -> second)), 100);

        assertThat(runner.chunkCount(LONG_SCRIPT)).isEqualTo(2);
        EvalRunner.Run run = runner.run(LONG_SCRIPT);

        assertThat(run.outcome().attempts()).extracting(Attempt::chunk).containsExactly(1, 2);
        assertThat(run.outcome().items()).hasSize(1);
    }
}
