package tracenotes.minutes;

/** 입력 스크립트가 서버 검사를 통과하지 못함. 메시지에 입력 본문을 넣지 않는다. */
public class InvalidScriptException extends RuntimeException {

    public enum Reason {
        BLANK,
        TOO_LONG,
        CONTAINS_NUL,
        /** 분할 결과 발언이 0개. 분할 뒤 서비스(W4·W6)에서 쓴다. */
        NO_UTTERANCES
    }

    private final Reason reason;

    public InvalidScriptException(Reason reason) {
        super("invalid script: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
