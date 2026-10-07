package tracenotes.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tracenotes.minutes.InvalidScriptException;

/** 예외를 공통 오류 응답으로 바꾼다. 응답과 로그에 입력 본문·예외 메시지를 넣지 않는다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidScriptException.class)
    ResponseEntity<ApiError> invalidScript(InvalidScriptException e) {
        return ResponseEntity.badRequest().body(ApiError.of(e.getReason()));
    }

    /** JSON 파싱 오류 메시지에는 입력 일부가 들어갈 수 있어 고정 문구로 바꾼다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable() {
        return ResponseEntity.badRequest().body(ApiError.MALFORMED_REQUEST);
    }

    /** 404·405·415 등 Spring MVC가 상태 코드를 정한 예외는 그 코드와 헤더(405의 Allow 등)를 유지한다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return ResponseEntity.status(status).headers(errorResponse.getHeaders()).body(ApiError.REQUEST_ERROR);
        }
        // 예외 메시지에 입력이 섞일 수 있어 클래스 이름과 경로만 남긴다
        log.error("unhandled {} on {}", e.getClass().getName(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.INTERNAL_ERROR);
    }
}
