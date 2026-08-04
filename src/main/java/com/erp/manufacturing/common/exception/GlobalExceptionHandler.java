package com.erp.manufacturing.common.exception;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception handler – converts all exceptions to {@link ApiResponse}.
 *
 * <p>Handler order (specific → general):
 * <ol>
 *   <li>{@link MultiErrorException}                    – aggregated field/list errors</li>
 *   <li>{@link AppException}                           – all custom business exceptions</li>
 *   <li>{@link MethodArgumentNotValidException}        – Bean Validation failures (@Valid)</li>
 *   <li>{@link ConstraintViolationException}           – @Validated on path/query params</li>
 *   <li>{@link HttpMessageNotReadableException}        – malformed / unparseable JSON body</li>
 *   <li>{@link MethodArgumentTypeMismatchException}    – wrong type in path/query (e.g. bad UUID)</li>
 *   <li>{@link MissingServletRequestParameterException}– required query param absent</li>
 *   <li>{@link HttpRequestMethodNotSupportedException} – wrong HTTP verb</li>
 *   <li>{@link AccessDeniedException}                  – Spring Security 403</li>
 *   <li>{@link ObjectOptimisticLockingFailureException}– @Version conflict</li>
 *   <li>{@link DataIntegrityViolationException}        – DB unique/FK constraint</li>
 *   <li>{@link Exception}                              – catch-all (never exposes stack traces)</li>
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

    // ── 3. Bean Validation failures (@Valid) ───────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        // Every violation is reported, including several on the same field. Binding-result
        // order is preserved so the summary and the errors array always agree.
        List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorResponse(
                        fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"))
                .toList();

        return badRequestWithFields(fieldErrors);
    }

    // ── 4. @Validated failures on path / query params ──────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {

        List<FieldErrorResponse> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();

        return badRequestWithFields(fieldErrors);
    }

    // ── 5. Malformed request body ──────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.debug("Unreadable request body at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ValidationErrorCode.INVALID_INPUT,
                        "Request body is missing or not valid JSON"));
    }

    // ── 6. Wrong type in path / query (e.g. malformed UUID) ────────────────

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String expectedType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "the expected type";
        List<FieldErrorResponse> fieldErrors = List.of(
                new FieldErrorResponse(ex.getName(), "must be a valid " + expectedType));

        return badRequestWithFields(fieldErrors);
    }

    // ── 7. Required query param absent ─────────────────────────────────────

    /**
     * Sibling of handler 6: a required {@code @RequestParam} that is present but unparseable is a type
     * mismatch, one that is absent altogether lands here. Without this handler the request falls
     * through to the catch-all and answers <b>500 INTERNAL_SERVER_ERROR</b> — telling the client the
     * server broke when in fact the call was malformed, and setting off 5xx alerts for what is an
     * ordinary client mistake. Reported as a field error so the caller learns <em>which</em> parameter
     * is missing.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex) {

        List<FieldErrorResponse> fieldErrors = List.of(
                new FieldErrorResponse(ex.getParameterName(), "is a required request parameter"));

        return badRequestWithFields(fieldErrors);
    }

    // ── 8. Wrong HTTP verb ─────────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ValidationErrorCode.INVALID_INPUT,
                        "HTTP method " + ex.getMethod() + " is not supported for this endpoint"));
    }

    // ── 9. Spring Security – access denied (403) ───────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(AuthErrorCode.ACCESS_DENIED));
    }

    // ── 10. Optimistic locking conflict (@Version) ─────────────────────────

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {

        log.warn("[{}] Optimistic lock conflict at {}: {}",
                BusinessErrorCode.CONCURRENT_MODIFICATION.code(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(BusinessErrorCode.CONCURRENT_MODIFICATION));
    }

    // ── 11. DB constraint violation (FK, unique index) ─────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("[{}] Data integrity violation at {}: {}",
                ValidationErrorCode.RESOURCE_ALREADY_EXISTS.code(),
                request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Data constraint violation"));
    }

    // ── 12. Catch-all – never expose internal details ──────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(
            Exception ex, HttpServletRequest request) {

        log.error("[{}] Unhandled exception at {}: {}",
                BusinessErrorCode.INTERNAL_SERVER_ERROR.code(),
                request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(BusinessErrorCode.INTERNAL_SERVER_ERROR));
    }

    /** Shared 400 shape: structured {@code errors} array plus the same content flattened into {@code message}. */
    private ResponseEntity<ApiResponse<Void>> badRequestWithFields(List<FieldErrorResponse> fieldErrors) {
        String summary = fieldErrors.stream()
                .map(fe -> fe.field() + ": " + fe.message())
                .collect(Collectors.joining("; "));

        log.debug("[{}] Validation failed: {}", ValidationErrorCode.INVALID_INPUT.code(), summary);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fieldErrors(ValidationErrorCode.INVALID_INPUT, summary, fieldErrors));
    }
}
