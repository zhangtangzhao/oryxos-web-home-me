package com.oryxos.web;

import com.oryxos.core.session.SessionArchivedException;
import com.oryxos.kb.EmbeddingNotConfiguredException;
import com.oryxos.kb.EmbeddingUnavailableException;
import com.oryxos.kb.KbConflictException;
import com.oryxos.kb.KbNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to the contract envelope + status codes
 * (contracts/rest-api.md): 400 illegal arguments / 404 missing resource /
 * 409 message to archived session (FR-029) / 500 internal.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Controllers raise this for missing sessions/agents (REST 404). */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(SessionArchivedException.class)
    public ResponseEntity<ApiResponse<Void>> handleArchived(SessionArchivedException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    // ---- knowledge base error mapping (contracts/rest-api.md) ----

    @ExceptionHandler(KbNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleKbNotFound(KbNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage() + " (KB_NOT_FOUND)");
    }

    @ExceptionHandler(KbConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleKbConflict(KbConflictException e) {
        return build(HttpStatus.CONFLICT, e.getMessage() + " (" + e.getCode() + ")");
    }

    @ExceptionHandler(EmbeddingUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmbeddingUnavailable(EmbeddingUnavailableException e) {
        return build(HttpStatus.BAD_GATEWAY, e.getMessage() + " (EMBEDDING_UNAVAILABLE)");
    }

    @ExceptionHandler(EmbeddingNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmbeddingNotConfigured(EmbeddingNotConfiguredException e) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage() + " (EMBEDDING_NOT_CONFIGURED)");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadParameter(Exception e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleInternal(Exception e) {
        log.error("REST 处理异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "内部错误: " + e.getMessage());
    }

    /** Spring MVC 404s (unknown paths) also get the contract envelope. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "资源不存在: " + e.getResourcePath());
    }

    private static ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
