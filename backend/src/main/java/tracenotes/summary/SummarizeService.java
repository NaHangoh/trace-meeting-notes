package tracenotes.summary;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracenotes.llm.LlmCallException;
import tracenotes.llm.LlmClient;
import tracenotes.minutes.Utterance;

/**
 * 정리 실행 (SPEC F2): 덩어리마다 LLM을 부르고, 응답을 검증해 덩어리 순서대로 이어 붙인다.
 * 형식 오류(응답이 JSON·스키마에 맞지 않음, OpenAI 형식이 아님)만 덩어리마다 최대 2회 다시 부른다.
 * 타임아웃·연결 실패·실행기 오류는 다시 부르지 않고 바로 실패한다. 한 덩어리가 실패하면 남은 덩어리는 부르지 않는다.
 * 로그에는 재시도 횟수와 실패 사유 종류(실행기 오류는 상태 코드)만 남긴다. 발언·응답 내용, 작업 ID는 남기지 않는다.
 */
public class SummarizeService {

    static final int MAX_RETRIES = 2;

    private static final Logger log = LoggerFactory.getLogger(SummarizeService.class);

    private final LlmClient llm;
    private final SummaryPromptBuilder builder;
    private final SummaryValidator validator;
    private final ScriptChunker chunker;

    public SummarizeService(LlmClient llm, SummaryPromptBuilder builder, SummaryValidator validator,
            ScriptChunker chunker) {
        this.llm = llm;
        this.builder = builder;
        this.validator = validator;
        this.chunker = chunker;
    }

    /**
     * @throws SummaryFailedException 실패 사유와 실패한 덩어리의 재시도 횟수
     */
    public List<SummaryItem> summarize(List<Utterance> utterances) {
        List<List<Utterance>> chunks = chunker.chunk(utterances);
        List<SummaryItem> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            result.addAll(summarizeChunk(chunks.get(i), i + 1, chunks.size()));
        }
        return result;
    }

    private List<SummaryItem> summarizeChunk(List<Utterance> chunk, int index, int total) {
        int retries = 0;
        while (true) {
            try {
                List<SummaryItem> items = validator.validate(llm.complete(builder.build(chunk)), chunk);
                if (retries > 0) {
                    log.info("summary chunk {}/{} succeeded retries={}", index, total, retries);
                }
                return items;
            } catch (InvalidSummaryOutputException e) {
                retries = retryOrFail(retries);
            } catch (LlmCallException e) {
                // switch 식이라 실패 종류가 새로 생기면 여기서 컴파일 오류가 난다 (빠진 분기가 무한 재시도가 되지 않게)
                retries = switch (e.kind()) {
                    case MALFORMED_RESPONSE -> retryOrFail(retries);
                    case TIMEOUT -> throw fail(SummaryFailure.TIMEOUT, retries, null);
                    case CONNECTION_REFUSED -> throw fail(SummaryFailure.CONNECTION_REFUSED, retries, null);
                    // LlmCallException 메시지는 상태 코드만 담은 고정 문구다 (응답 본문 없음)
                    case HTTP_ERROR -> throw fail(SummaryFailure.LLM_ERROR, retries, e.getMessage());
                };
            }
        }
    }

    private static int retryOrFail(int retries) {
        if (retries >= MAX_RETRIES) {
            throw fail(SummaryFailure.INVALID_OUTPUT, retries, null);
        }
        return retries + 1;
    }

    private static SummaryFailedException fail(SummaryFailure reason, int retries, String status) {
        if (status == null) {
            log.warn("summary failed: {} retries={}", reason, retries);
        } else {
            log.warn("summary failed: {} retries={} ({})", reason, retries, status);
        }
        return new SummaryFailedException(reason, retries);
    }
}
