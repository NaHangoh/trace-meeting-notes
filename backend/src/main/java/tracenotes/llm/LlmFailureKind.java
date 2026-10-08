package tracenotes.llm;

/** LLM 호출 실패 종류. 응답 내용이 스키마에 맞지 않는 형식 오류는 정리 단계(W4)에서 판정한다. */
public enum LlmFailureKind {
    /** 호출 타임아웃 (app.llm.timeout). */
    TIMEOUT,
    /** 실행기에 연결하지 못함 (실행기가 꺼져 있음 등). */
    CONNECTION_REFUSED,
    /** 실행기가 2xx가 아닌 상태 코드로 답함. */
    HTTP_ERROR,
    /** 응답이 OpenAI 형식이 아님 (choices[0].message.content 없음). */
    MALFORMED_RESPONSE
}
