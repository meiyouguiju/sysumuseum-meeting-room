package edu.sysu.museummeetingroom.common.web;

import edu.sysu.museummeetingroom.common.api.ApiErrorResponse;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.errorCode(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception exception, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fields = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                        .toList()
                : List.of();
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_VALIDATION_ERROR",
                "请求参数不合法。",
                fields,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_BODY_INVALID",
                "请求体格式不合法。",
                List.of(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIdFilter.getRequestId(request);
        log.error(
                "Unhandled exception, requestId={}, method={}, uri={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "服务暂时无法处理请求。",
                List.of(),
                request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldError> fields,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, fields, RequestIdFilter.getRequestId(request)));
    }
}
