package tracenotes.summary;

/** 정리 실패 사유 (SPEC F2 "실패 처리"). SERVER_RESTART는 대기열·작업자(W6)에서 추가한다. */
public enum SummaryFailure {
    /** 응답이 JSON·스키마에 맞지 않거나 OpenAI 형식이 아님. 최대 2회 다시 부른 뒤. */
    INVALID_OUTPUT,
    /** 호출 타임아웃. 다시 부르지 않음. */
    TIMEOUT,
    /** 연결 거부·연결 타임아웃·응답 도중 끊김. 다시 부르지 않음. */
    CONNECTION_REFUSED,
    /** 실행기가 2xx가 아닌 상태로 답함. 다시 부르지 않음. */
    LLM_ERROR
}
