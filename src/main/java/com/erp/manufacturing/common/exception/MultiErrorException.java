package com.erp.manufacturing.common.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exception that aggregates multiple field-level or message-level errors into one throw.
 *
 * <p>This is useful when you want to collect <em>all</em> validation failures before
 * throwing, rather than stopping at the first error. Returned HTTP status and error
 * code are taken from the provided {@link ErrorCode}.
 *
 * <p>Typically created via:
 * <ul>
 *   <li>{@link ExceptionFactory#withErrors(ErrorCode, Map)} – field → message pairs</li>
 *   <li>{@link ExceptionFactory#withErrors(ErrorCode, List)} – plain message list</li>
 * </ul>
 *
 * <h3>JSON response shape</h3>
 * <pre>{@code
 * {
 *   "code":    "VAL_001",
 *   "message": "username: must not be blank; email: invalid format",
 *   "errors": {
 *     "username": "must not be blank",
 *     "email":    "invalid format"
 *   }
 * }
 * }</pre>
 */
@Getter
public class MultiErrorException extends AppException {

    /** Field-level errors: {@code fieldName → errorMessage}. May be empty if list-style errors used. */
    private final Map<String, String> fieldErrors;

    /** Plain error messages. May be empty if map-style errors used. */
    private final List<String> errors;

    /**
     * Constructor for field-level errors (e.g. DTO validation aggregation).
     */
    public MultiErrorException(ErrorCode code, Map<String, String> fieldErrors) {
        super(code, buildMessage(fieldErrors));
        this.fieldErrors = Collections.unmodifiableMap(fieldErrors);
        this.errors      = Collections.emptyList();
    }

    /**
     * Constructor for plain error message list (e.g. business rule violations).
     */
    public MultiErrorException(ErrorCode code, List<String> errors) {
        super(code, String.join("; ", errors));
        this.fieldErrors = Collections.emptyMap();
        this.errors      = Collections.unmodifiableList(errors);
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static String buildMessage(Map<String, String> fieldErrors) {
        return fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));
    }
}
