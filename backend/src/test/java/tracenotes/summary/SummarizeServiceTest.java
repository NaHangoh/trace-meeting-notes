package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.llm.FixtureLlmClient;
import tracenotes.llm.LlmCallException;
import tracenotes.llm.LlmFailureKind;
import tracenotes.llm.LlmRequest;
import tracenotes.minutes.Utterance;

/** SPEC F2 정리 실행과 실패 처리. 실제 LLM 대신 FixtureLlmClient를 쓴다. */
@ExtendWith(OutputCaptureExtension.class)
class SummarizeServiceTest {

    private static final String TEMPLATE = "회의를 정리하라. 발언은 {{DELIMITER}} 블록 안에만 있다.";
    private static final String MARKER = "비밀안건표식";

    /** 줄 하나 30자, 100자 덩어리에 3줄씩 → 발언 9개는 덩어리 3개. */
    private static final List<Utterance> NINE = IntStream.rangeClosed(1, 9)
            .mapToObj(n -> new Utterance(n, "참석자", "가".repeat(21)))
            .toList();

    private final FixtureLlmClient llm = new FixtureLlmClient();
    private final AtomicInteger calls = new AtomicInteger();
    private final SummarizeService service = new SummarizeService(
            llm, new SummaryPromptBuilder(TEMPLATE), new SummaryValidator(JsonMapper.builder().build()),
            new ScriptChunker(100));

    private void respond(Function<LlmRequest, String> responder) {
        llm.respondWith(request -> {
            calls.incrementAndGet();
            return responder.apply(request);
        });
    }

    /** 요청 블록의 첫 발언 번호. */
    private static int firstNumber(LlmRequest request) {
        Matcher matcher = Pattern.compile("^\\[(\\d+)\\]", Pattern.MULTILINE).matcher(request.userContent());
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static String discussion(String text, int evidence) {
        return "{\"items\":[{\"type\":\"DISCUSSION\",\"text\":\"" + text + "\",\"evidence\":[" + evidence
                + "],\"assignee\":null,\"due\":null}]}";
    }

    @Test
    void resultsConcatenatedInChunkOrder() {
        respond(request -> discussion("덩어리 " + firstNumber(request), firstNumber(request)));

        List<SummaryItem> result = service.summarize(NINE);

        assertThat(calls).hasValue(3);
        assertThat(result).extracting(SummaryItem::text).containsExactly("덩어리 1", "덩어리 4", "덩어리 7");
        assertThat(result).extracting(SummaryItem::evidence).containsExactly(List.of(1), List.of(4), List.of(7));
    }

    /** 근거 번호는 그 덩어리 범위로 확인한다. 다른 덩어리의 번호는 버린다. */
    @Test
    void evidenceIsCheckedAgainstItsOwnChunk() {
        respond(request -> discussion("항상 1번 인용", 1));

        List<SummaryItem> result = service.summarize(NINE);

        assertThat(result).extracting(SummaryItem::hasEvidence).containsExactly(true, false, false);
    }

    @Test
    void invalidOutputRetriesTwiceThenFails() {
        respond(request -> "JSON이 아닌 응답");

        assertThatThrownBy(() -> service.summarize(NINE))
                .isInstanceOfSatisfying(SummaryFailedException.class, e -> {
                    assertThat(e.reason()).isEqualTo(SummaryFailure.INVALID_OUTPUT);
                    assertThat(e.retries()).isEqualTo(2);
                });
        assertThat(calls).hasValue(3);
    }

    @Test
    void invalidOutputThenValidSucceeds() {
        AtomicInteger attempt = new AtomicInteger();
        respond(request -> attempt.incrementAndGet() == 1 ? "{}" : discussion("의견", firstNumber(request)));

        List<SummaryItem> result = service.summarize(NINE.subList(0, 3));

        assertThat(calls).hasValue(2);
        assertThat(result).extracting(SummaryItem::text).containsExactly("의견");
    }

    /** 재시도는 덩어리마다 따로 센다. */
    @Test
    void retriesAreCountedPerChunk() {
        AtomicInteger attempt = new AtomicInteger();
        respond(request -> attempt.incrementAndGet() % 3 == 0 ? discussion("의견", firstNumber(request)) : "{}");

        List<SummaryItem> result = service.summarize(NINE.subList(0, 6));

        assertThat(calls).hasValue(6);
        assertThat(result).hasSize(2);
    }

    /** OpenAI 형식이 아닌 응답은 형식 오류와 같이 최대 2회 다시 부른다 (SPEC F2 실패 처리 표). */
    @Test
    void malformedResponseIsRetriedAsInvalidOutput() {
        respond(request -> {
            throw new LlmCallException(LlmFailureKind.MALFORMED_RESPONSE, "malformed");
        });

        assertThatThrownBy(() -> service.summarize(NINE))
                .isInstanceOfSatisfying(SummaryFailedException.class,
                        e -> assertThat(e.reason()).isEqualTo(SummaryFailure.INVALID_OUTPUT));
        assertThat(calls).hasValue(3);
    }

    @Test
    void timeoutFailsWithoutRetry() {
        respond(request -> {
            throw new LlmCallException(LlmFailureKind.TIMEOUT, "timeout");
        });

        assertThatThrownBy(() -> service.summarize(NINE))
                .isInstanceOfSatisfying(SummaryFailedException.class, e -> {
                    assertThat(e.reason()).isEqualTo(SummaryFailure.TIMEOUT);
                    assertThat(e.retries()).isZero();
                });
        assertThat(calls).hasValue(1);
    }

    @Test
    void connectionRefusedFailsWithoutRetry() {
        respond(request -> {
            throw new LlmCallException(LlmFailureKind.CONNECTION_REFUSED, "refused");
        });

        assertThatThrownBy(() -> service.summarize(NINE))
                .isInstanceOfSatisfying(SummaryFailedException.class,
                        e -> assertThat(e.reason()).isEqualTo(SummaryFailure.CONNECTION_REFUSED));
        assertThat(calls).hasValue(1);
    }

    /** 실행기가 2xx가 아닌 상태로 답하면 LLM_ERROR이고 다시 부르지 않는다. */
    @Test
    void httpErrorIsLlmErrorWithoutRetry() {
        respond(request -> {
            throw new LlmCallException(LlmFailureKind.HTTP_ERROR, "LLM call failed with status 503");
        });

        assertThatThrownBy(() -> service.summarize(NINE))
                .isInstanceOfSatisfying(SummaryFailedException.class,
                        e -> assertThat(e.reason()).isEqualTo(SummaryFailure.LLM_ERROR));
        assertThat(calls).hasValue(1);
    }

    /** SPEC F2: 6,000자(여기서는 100자)를 넘는 발언은 잘려 보내지고, 각 덩어리 결과의 근거 번호는 원래 발언 번호로 유지된다. */
    @Test
    void oversizedUtteranceResultsKeepOriginalNumber() {
        List<Utterance> utterances = List.of(new Utterance(2, "참석자", "가".repeat(250)));
        respond(request -> discussion("조각 의견", 2));

        List<SummaryItem> result = service.summarize(utterances);

        assertThat(calls.get()).isGreaterThan(1);
        assertThat(result).hasSize(calls.get()).allSatisfy(item -> {
            assertThat(item.hasEvidence()).isTrue();
            assertThat(item.evidence()).containsExactly(2);
        });
    }

    /** SPEC F2: 실행기 오류(LLM_ERROR)의 로그에는 HTTP 상태 코드만 있다. */
    @Test
    void llmErrorLogHasStatusCodeOnly(CapturedOutput output) {
        respond(request -> {
            throw new LlmCallException(LlmFailureKind.HTTP_ERROR, "LLM call failed with status 503");
        });

        assertThatThrownBy(() -> service.summarize(List.of(new Utterance(1, "참석자", MARKER))))
                .isInstanceOf(SummaryFailedException.class);

        assertThat(output.getAll()).contains("LLM_ERROR").contains("503").doesNotContain(MARKER);
    }

    /** 한 덩어리가 실패하면 남은 덩어리는 부르지 않는다. */
    @Test
    void failureStopsRemainingChunks() {
        respond(request -> {
            if (firstNumber(request) == 4) {
                throw new LlmCallException(LlmFailureKind.TIMEOUT, "timeout");
            }
            return discussion("의견", firstNumber(request));
        });

        assertThatThrownBy(() -> service.summarize(NINE)).isInstanceOf(SummaryFailedException.class);
        assertThat(calls).hasValue(2);
    }

    /** 로그에는 재시도 횟수와 실패 사유 종류만 남는다. 발언·LLM 응답 내용은 남지 않는다. */
    @Test
    void logsHaveOnlyRetryCountAndFailureKind(CapturedOutput output) {
        List<Utterance> utterances = List.of(new Utterance(1, "참석자 " + MARKER, "내용 " + MARKER));
        respond(request -> "형식이 틀린 응답 secretOutputMarker");

        assertThatThrownBy(() -> service.summarize(utterances)).isInstanceOf(SummaryFailedException.class);

        assertThat(output.getAll()).contains("INVALID_OUTPUT").contains("retries=2");
        assertThat(output.getAll()).doesNotContain(MARKER).doesNotContain("secretOutputMarker");
    }

    /** 실패 예외의 메시지에도 내용이 들어가지 않는다. */
    @Test
    void failureMessageHasNoContent() {
        respond(request -> {
            throw new LlmCallException(LlmFailureKind.HTTP_ERROR, "LLM call failed with status 401");
        });

        assertThatThrownBy(() -> service.summarize(List.of(new Utterance(1, "참석자", MARKER))))
                .isInstanceOf(SummaryFailedException.class)
                .message().doesNotContain(MARKER);
    }
}
