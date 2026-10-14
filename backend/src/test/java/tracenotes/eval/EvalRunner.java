package tracenotes.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import tracenotes.eval.RunOutcome.Attempt;
import tracenotes.llm.LlmCallException;
import tracenotes.llm.LlmClient;
import tracenotes.llm.LlmFailureKind;
import tracenotes.minutes.Utterance;
import tracenotes.summary.InvalidSummaryOutputException;
import tracenotes.summary.ScriptChunker;
import tracenotes.summary.SummaryItem;
import tracenotes.summary.SummaryPromptBuilder;
import tracenotes.summary.SummaryValidation;
import tracenotes.summary.SummaryValidator;

/**
 * 케이스 하나를 제품 정리와 같은 순서로 한 번 실행한다: 덩어리마다 요청을 만들고, 부르고, 검증한다.
 * 형식 오류만 덩어리마다 최대 2회 다시 부르고(SummarizeService와 같음), 그 밖의 실패는 바로 멈춘다.
 * 호출마다 시간과 검증기가 지운 개수를 남긴다 (SummarizeService는 이 값을 밖으로 내지 않아 따로 돈다).
 */
final class EvalRunner {

    static final int MAX_RETRIES = 2;

    /**
     * @param outcome 지표 계산에 쓰는 결과
     * @param responses 받은 응답 원문 (호출 순서). 저장소 밖에만 저장한다
     */
    record Run(RunOutcome outcome, List<String> responses) {
    }

    private final LlmClient llm;
    private final SummaryPromptBuilder builder;
    private final SummaryValidator validator;
    private final ScriptChunker chunker;
    private final LongSupplier clockMs;

    EvalRunner(LlmClient llm, SummaryPromptBuilder builder, SummaryValidator validator, ScriptChunker chunker,
            LongSupplier clockMs) {
        this.llm = llm;
        this.builder = builder;
        this.validator = validator;
        this.chunker = chunker;
        this.clockMs = clockMs;
    }

    /** 덩어리 수 (예상 호출 수 계산용, LLM을 부르지 않는다). */
    int chunkCount(List<Utterance> utterances) {
        return chunker.chunk(utterances).size();
    }

    Run run(List<Utterance> utterances) {
        return run(utterances, builder);
    }

    /** 이 실행에만 쓸 요청 생성기로 실행한다 (eval은 실행마다 구분자를 정해 버전끼리 맞춘다). */
    Run run(List<Utterance> utterances, SummaryPromptBuilder builder) {
        List<List<Utterance>> chunks = chunker.chunk(utterances);
        List<SummaryItem> items = new ArrayList<>();
        List<Attempt> attempts = new ArrayList<>();
        List<String> responses = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            List<Utterance> chunk = chunks.get(i);
            int chunkNo = i + 1;
            boolean done = false;
            for (int attempt = 0; attempt <= MAX_RETRIES && !done; attempt++) {
                long started = clockMs.getAsLong();
                String response;
                try {
                    response = llm.complete(builder.build(chunk));
                } catch (LlmCallException e) {
                    if (e.kind() != LlmFailureKind.MALFORMED_RESPONSE) {
                        // 응답이 없는 실패는 호출로 세지 않고 바로 멈춘다 (SummarizeService와 같음)
                        return new Run(new RunOutcome(items, attempts, e.kind().name()), responses);
                    }
                    attempts.add(new Attempt(chunkNo, clockMs.getAsLong() - started, false, null));
                    responses.add("(" + e.kind().name() + ")");
                    continue;
                }
                long elapsed = clockMs.getAsLong() - started;
                responses.add(response);
                try {
                    SummaryValidation validation = validator.check(response, chunk);
                    attempts.add(new Attempt(chunkNo, elapsed, true, validation.removed()));
                    items.addAll(validation.items());
                    done = true;
                } catch (InvalidSummaryOutputException e) {
                    attempts.add(new Attempt(chunkNo, elapsed, false, null));
                }
            }
            if (!done) {
                return new Run(new RunOutcome(items, attempts, "INVALID_OUTPUT"), responses);
            }
        }
        return new Run(new RunOutcome(items, attempts, null), responses);
    }
}
