package tracenotes.common;

import tracenotes.minutes.InvalidScriptException.Reason;

/** 공통 오류 응답. code와 고정 문구만 담고 입력 내용이나 내부 예외 메시지는 넣지 않는다. */
public record ApiError(String code, String message) {

    static final ApiError PAYLOAD_TOO_LARGE = new ApiError("PAYLOAD_TOO_LARGE", "요청 크기가 상한을 넘었습니다.");
    static final ApiError MALFORMED_REQUEST = new ApiError("MALFORMED_REQUEST", "요청 형식이 올바르지 않습니다.");
    static final ApiError TOO_MANY_REQUESTS = new ApiError("TOO_MANY_REQUESTS", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
    static final ApiError REQUEST_ERROR = new ApiError("REQUEST_ERROR", "요청을 처리할 수 없습니다.");
    static final ApiError INTERNAL_ERROR = new ApiError("INTERNAL_ERROR", "서버 오류가 발생했습니다.");

    static ApiError of(Reason reason) {
        String message = switch (reason) {
            case BLANK -> "입력이 비어 있습니다.";
            case TOO_LONG -> "입력이 최대 글자 수를 넘었습니다.";
            case CONTAINS_NUL -> "텍스트가 아닌 문자(NUL)가 들어 있습니다.";
            case NO_UTTERANCES -> "발언이 없습니다. 화자 줄 아래에 내용을 입력하세요.";
        };
        return new ApiError(reason.name(), message);
    }

    /** 필터처럼 메시지 변환기 밖에서 쓸 JSON. code와 message는 따옴표·역슬래시가 없는 고정 문구다. */
    String toJson() {
        return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
    }
}
