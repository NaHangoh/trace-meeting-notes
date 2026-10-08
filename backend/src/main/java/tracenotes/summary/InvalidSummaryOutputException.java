package tracenotes.summary;

/**
 * LLM 응답이 정리 결과 형식(JSON 객체, items 배열)이 아님. 실패 사유 INVALID_OUTPUT이고 다시 부를 수 있다 (SPEC F2).
 * 메시지에는 응답 내용을 넣지 않는다.
 */
public class InvalidSummaryOutputException extends RuntimeException {

    public InvalidSummaryOutputException(String message) {
        super(message);
    }
}
