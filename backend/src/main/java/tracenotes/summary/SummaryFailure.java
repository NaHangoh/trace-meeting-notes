package tracenotes.summary;

/** 정리 실패 사유 (SPEC F2 "실패 처리"). 모두 "다시 시도"로 사용자가 다시 실행할 수 있다. */
public enum SummaryFailure {
    /** 응답이 JSON·스키마에 맞지 않거나 OpenAI 형식이 아님. 최대 2회 다시 부른 뒤. */
    INVALID_OUTPUT,
    /** 호출 타임아웃. 다시 부르지 않음. */
    TIMEOUT,
    /** 연결 거부·연결 타임아웃·응답 도중 끊김. 다시 부르지 않음. */
    CONNECTION_REFUSED,
    /** 실행기가 2xx가 아닌 상태로 답함. 다시 부르지 않음. */
    LLM_ERROR,
    /** 서버가 다시 시작됨(대기·실행 중이던 작업), 또는 서버 종료로 실행 중 호출이 중단됨. */
    SERVER_RESTART,
    /** 그 밖의 예상 밖 서버 오류(결과 저장 실패, 요청 조립 오류 등). 자동으로 다시 실행하지 않음. */
    INTERNAL_ERROR
}
