package tracenotes.llm;

import java.util.Objects;
import java.util.function.Function;

/**
 * 테스트용 LLM (app.llm.provider=fixture). 실제 LLM을 부르지 않고 정해 둔 응답을 돌려준다.
 * 자동 테스트는 이 구현만 쓴다 (rules/llm.md).
 */
public class FixtureLlmClient implements LlmClient {

    /** 응답을 정하지 않았을 때: 항목 없음. */
    public static final String EMPTY_RESULT = "{\"items\":[]}";

    private volatile Function<LlmRequest, String> responder = request -> EMPTY_RESULT;

    /** 요청마다 돌려줄 응답(또는 던질 예외)을 정한다. */
    public void respondWith(Function<LlmRequest, String> responder) {
        this.responder = Objects.requireNonNull(responder);
    }

    public void reset() {
        this.responder = request -> EMPTY_RESULT;
    }

    @Override
    public String complete(LlmRequest request) {
        return responder.apply(request);
    }
}
