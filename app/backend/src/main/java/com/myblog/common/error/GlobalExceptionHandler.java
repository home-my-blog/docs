package com.myblog.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 오류 응답 모양: { "error": { code, message, fields?, ...details } }. 내부 정보는 내보내지 않는다 (NF-06). */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handle(ApiException e) {
        return body(e.code(), e.getMessage(), e.fields(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handle(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String first = fields.values().stream().findFirst().orElse(ErrorCode.VALIDATION_FAILED.message());
        return body(ErrorCode.VALIDATION_FAILED, first, fields, Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return body(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), Map.of(), Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(ErrorCode.INVALID_IMAGE, ErrorCode.INVALID_IMAGE.message(), Map.of(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return body(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message(), Map.of(), Map.of());
    }

    public static Map<String, Object> errorBody(ErrorCode code, String message, Map<String, String> fields,
                                                Map<String, Object> details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code.name());
        error.put("message", message);
        if (!fields.isEmpty()) {
            error.put("fields", fields);
        }
        error.putAll(details);
        return Map.of("error", error);
    }

    private static ResponseEntity<Map<String, Object>> body(ErrorCode code, String message, Map<String, String> fields,
                                                           Map<String, Object> details) {
        return ResponseEntity.status(code.status()).body(errorBody(code, message, fields, details));
    }
}
