package com.erp.manufacturing.common.exception;

import com.erp.manufacturing.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler – converts all exceptions to {@link ApiResponse}.
 *
 * <p>Handler order (specific → general):
 * <ol>
 *   <li>{@link MultiErrorException}               – aggregated field/list errors</li>
 *   <li>{@link AppException}                      – all custom business exceptions</li>
 *   <li>{@link MethodArgumentNotValidException}   – Bean Validation failures (@Valid)</li>
 *   <li>{@link AccessDeniedException}             – Spring Security 403</li>
 *   <li>{@link DataIntegrityViolationException}   – DB unique/FK constraint</li>
 *   <li>{@link Exception}                         – catch-all (never exposes stack traces)</li>
 * </ol>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 1. Multi-error (aggregated validation / business rules) ────────────

    @ExceptionHandler(MultiErrorException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultiError(
            MultiErrorException ex, HttpServletRequest request) {

        log.warn("[{}] {} – {} (multi-error, {} errors)",
                ex.getCode(), request.getRequestURI(), ex.getMessage(),
                ex.hasFieldErrors() ? ex.getFieldErrors().size() : ex.getErrors().size());

        if (ex.hasFieldErrors()) {
            return ResponseEntity.status(ex.getHttpStatus())
                    .body(ApiResponse.fieldErrors(ex.getErrorCode(), ex.getMessage(), ex.getFieldErrors()));
        }
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.multiErrors(ex.getErrorCode(), ex.getErrors()));
    }

    // ── 2. Single custom business exception ────────────────────────────────

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(
            AppException ex, HttpServletRequest request) {

        log.warn("[{}] {} – {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    // ── 3. Bean Validation failures (@Valid / @Validated) ──────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing  // keep first error per field
                ));

        String summary = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));

        log.debug("[VAL_001] Validation failed: {}", summary);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fieldErrors(ValidationErrorCode.INVALID_INPUT, summary, fieldErrors));
    }

    // ── 4. Spring Security – access denied (403) ────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(AuthErrorCode.ACCESS_DENIED));
    }

    // ── 5. DB constraint violation (FK, unique index) ──────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("[VAL_021] Data integrity violation at {}: {}",
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Data constraint violation"));
    }

    // ── 6. Catch-all – never expose internal details ────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(
            Exception ex, HttpServletRequest request) {

        log.error("[BIZ_099] Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(BusinessErrorCode.INTERNAL_SERVER_ERROR));
    }
}
