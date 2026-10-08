package tracenotes.llm;

/** LLM 호출 실패. 메시지에는 실패 종류와 상태 코드만 넣는다 (키, 요청·응답 내용 없음). */
public class LlmCallException extends RuntimeException {

    private final LlmFailureKind kind;

    public LlmCallException(LlmFailureKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmFailureKind kind() {
        return kind;
    }
}
