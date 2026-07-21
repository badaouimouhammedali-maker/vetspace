package com.vetspace.web.error;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/** Maps every error to the {error, message, timestamp} shape mandated by CLAUDE.md; never leaks a stack trace to the client. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 4MB limit");
    }

    @ExceptionHandler(com.vetspace.admin.ImportValidationException.class)
    public ResponseEntity<ImportErrorBody> handleImportValidation(com.vetspace.admin.ImportValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ImportErrorBody(HttpStatus.BAD_REQUEST.getReasonPhrase(), ex.getMessage(), Instant.now(),
                com.vetspace.web.RequestIdFilter.current(), ex.getErrors()));
    }

    /** Standard error shape plus the per-row detail list the import contract promises. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record ImportErrorBody(String error, String message, Instant timestamp, String requestId,
                                   java.util.List<com.vetspace.admin.dto.QuestionDtos.ImportRowError> errors) {
    }

    @ExceptionHandler(com.vetspace.access.SubscriptionRequiredException.class)
    public ResponseEntity<ApiError> handleSubscriptionRequired(com.vetspace.access.SubscriptionRequiredException ex) {
        // Machine-readable error code (not the HTTP reason phrase): the frontend routes on it.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiError("SUBSCRIPTION_REQUIRED", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        // Must not fall through to the generic handler: method-security denials surface here,
        // and turning them into 500s would both confuse clients and hide the real signal.
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * A request to a path with no handler is a 404, not a server fault.
     *
     * <p>Without this it fell through to the generic handler: every typo'd URL and every
     * probe for a route that does not exist answered 500 and logged a full stack trace
     * as "Unhandled exception". That misreads as the server being broken — it sent us
     * chasing a redeem endpoint that was fine and never called — and it buries real
     * 500s in noise from scanners hitting /wp-login.php.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        // Logged at debug: a missing path is a client mistake, not an incident.
        log.debug("No handler for {}", ex.getResourcePath());
        return error(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.getReasonPhrase(), message, Instant.now()));
    }
}
