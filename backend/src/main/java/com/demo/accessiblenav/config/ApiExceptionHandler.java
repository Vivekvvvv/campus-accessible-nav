package com.demo.accessiblenav.config;

import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.exception.ErrorCode;
import com.demo.accessiblenav.exception.ResourceNotFoundException;
import com.demo.accessiblenav.exception.RouteException;
import com.demo.accessiblenav.exception.FileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        log.warn("业务异常: code={}, message={}, path={}",
                ex.getCode(), ex.getMessage(), request.getRequestURI());
        return build(status, ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("资源未找到: {}, path={}", ex.getMessage(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * 处理路由计算异常
     */
    @ExceptionHandler(RouteException.class)
    public ResponseEntity<ApiErrorResponse> handleRouteException(RouteException ex, HttpServletRequest request) {
        log.warn("路由异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * 处理文件异常
     */
    @ExceptionHandler(FileException.class)
    public ResponseEntity<ApiErrorResponse> handleFileException(FileException ex, HttpServletRequest request) {
        log.warn("文件异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, status.name(), message, request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> fields = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("field", error.getField());
            item.put("message", error.getDefaultMessage());
            fields.add(item);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fields", fields);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", request.getRequestURI(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("field", violation.getPropertyPath().toString());
            item.put("message", violation.getMessage());
            violations.add(item);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("violations", violations);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", request.getRequestURI(), details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request body", request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = String.format("Invalid value for parameter '%s'", ex.getName());
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message, request.getRequestURI(), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = String.format("Missing required parameter '%s'", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message, request.getRequestURI(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_STATE", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Forbidden", request.getRequestURI(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized", request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error", request.getRequestURI(), null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, String path, Object details) {
        // 添加追踪ID用于日志关联
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        ApiErrorResponse body = new ApiErrorResponse(code, message, traceId, status.value(), path, Instant.now(), details);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 将错误码映射到HTTP状态码
     */
    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode errorCode) {
        switch (errorCode) {
            case USER_NOT_FOUND:
            case OBSTACLE_NOT_FOUND:
            case GRAPH_SNAPSHOT_NOT_FOUND:
            case CHANGE_REQUEST_NOT_FOUND:
            case FILE_NOT_FOUND:
            case RESOURCE_NOT_FOUND:
                return HttpStatus.NOT_FOUND;

            case UNAUTHORIZED:
            case TOKEN_EXPIRED:
            case TOKEN_INVALID:
            case REFRESH_TOKEN_EXPIRED:
            case REFRESH_TOKEN_INVALID:
            case INVALID_CREDENTIALS:
                return HttpStatus.UNAUTHORIZED;

            case FORBIDDEN:
                return HttpStatus.FORBIDDEN;

            case USER_ALREADY_EXISTS:
            case OBSTACLE_ALREADY_REPORTED:
            case OBSTACLE_ALREADY_REVIEWED:
            case GRAPH_VERSION_CONFLICT:
            case CHANGE_REQUEST_ALREADY_PROCESSED:
                return HttpStatus.CONFLICT;

            case INTERNAL_ERROR:
                return HttpStatus.INTERNAL_SERVER_ERROR;

            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}
