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
import org.springframework.web.servlet.HandlerMapping;
import tracenotes.minutes.InvalidScriptException;
import tracenotes.summary.JobNotFoundException;
import tracenotes.summary.JobNotRetryableException;
import tracenotes.summary.QueueFullException;

/** 예외를 공통 오류 응답으로 바꾼다. 응답과 로그에 입력 본문·예외 메시지를 넣지 않는다. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidScriptException.class)
    ResponseEntity<ApiError> invalidScript(InvalidScriptException e) {
        return ResponseEntity.badRequest().body(ApiError.of(e.getReason()));
    }

    @ExceptionHandler(QueueFullException.class)
    ResponseEntity<ApiError> queueFull() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError.QUEUE_FULL);
    }

    @ExceptionHandler(JobNotFoundException.class)
    ResponseEntity<ApiError> jobNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.NOT_FOUND);
    }

    @ExceptionHandler(JobNotRetryableException.class)
    ResponseEntity<ApiError> jobNotRetryable() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.NOT_RETRYABLE);
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
        // 예외 메시지에 입력이 섞일 수 있어 클래스 이름과 경로 패턴만 남긴다.
        // 실제 URI에는 작업 ID(곧 열람 권한)가 들어갈 수 있어(/api/jobs/{id}/retry) 매핑 패턴을 쓴다
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        log.error("unhandled {} on {}", e.getClass().getName(), pattern != null ? pattern : "unmapped path");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.INTERNAL_ERROR);
    }
}
