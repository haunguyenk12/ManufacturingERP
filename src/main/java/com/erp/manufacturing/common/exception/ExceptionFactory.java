package com.erp.manufacturing.common.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Central factory for creating {@link AppException} instances.
 *
 * <p>Using a factory instead of {@code new AppException(...)} directly:
 * <ul>
 *   <li>Provides named, intent-revealing constructors (e.g. {@code notFound}, {@code alreadyExists})</li>
 *   <li>Ensures consistent message patterns across the codebase</li>
 *   <li>Makes throw-sites easy to grep and audit</li>
 * </ul>
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * // Simple – use the error code's default message
 * throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND);
 *
 * // With entity name
 * throw ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId);
 *
 * // Already exists
 * throw ExceptionFactory.alreadyExists(ValidationErrorCode.USERNAME_ALREADY_EXISTS, "admin");
 *
 * // Auth
 * throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_EXPIRED);
 *
 * // Custom message
 * throw ExceptionFactory.custom(BusinessErrorCode.BUSINESS_RULE_VIOLATION, "Stock cannot go below safety level");
 *
 * // Multiple validation errors at once
 * throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
 *         Map.of("username", "must not be blank", "email", "invalid format"));
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExceptionFactory {

    // ── Not Found (404) ────────────────────────────────────────────────────

    /**
     * Throws a 404-style exception using the code's default message.
     */
    public static AppException notFound(ErrorCode code) {
        return new AppException(code);
    }

    /**
     * Throws a 404-style exception with a formatted message:
     * {@code "{resourceName} not found with id: {id}"}
     */
    public static AppException notFound(ErrorCode code, String resourceName, Object id) {
        return new AppException(code, resourceName + " not found with id: " + id);
    }

    /**
     * Throws a 404-style exception with a custom message.
     */
    public static AppException notFound(ErrorCode code, String customMessage) {
        return new AppException(code, customMessage);
    }

    // ── Already Exists (409) ───────────────────────────────────────────────

    /**
     * Throws a 409-style exception using the code's default message.
     */
    public static AppException alreadyExists(ErrorCode code) {
        return new AppException(code);
    }

    /**
     * Throws a 409-style exception with a formatted message:
     * {@code "{resourceName} already exists: {value}"}
     */
    public static AppException alreadyExists(ErrorCode code, String resourceName, Object value) {
        return new AppException(code, resourceName + " already exists: " + value);
    }

    /**
     * Throws a 409-style exception with a custom message.
     */
    public static AppException alreadyExists(ErrorCode code, String customMessage) {
        return new AppException(code, customMessage);
    }

    // ── Unauthorized (401) ─────────────────────────────────────────────────

    /**
     * Throws a 401-style exception using the code's default message.
     */
    public static AppException unauthorized(ErrorCode code) {
        return new AppException(code);
    }

    /**
     * Throws a 401-style exception with a custom message.
     */
    public static AppException unauthorized(ErrorCode code, String customMessage) {
        return new AppException(code, customMessage);
    }

    // ── Forbidden (403) ────────────────────────────────────────────────────

    /**
     * Throws a 403-style exception using the code's default message.
     */
    public static AppException forbidden(ErrorCode code) {
        return new AppException(code);
    }

    // ── Business Rule (422) ────────────────────────────────────────────────

    /**
     * Throws a business rule violation exception with a custom message.
     */
    public static AppException businessRule(ErrorCode code, String customMessage) {
        return new AppException(code, customMessage);
    }

    /**
     * Throws a business rule violation exception using the code's default message.
     */
    public static AppException businessRule(ErrorCode code) {
        return new AppException(code);
    }

    // ── Custom / Generic ───────────────────────────────────────────────────

    /**
     * Creates a fully custom exception with the given error code and message.
     * Use this when none of the named factories fit.
     */
    public static AppException custom(ErrorCode code, String customMessage) {
        return new AppException(code, customMessage);
    }

    /**
     * Creates a custom exception wrapping a root cause (for infrastructure errors).
     */
    public static AppException custom(ErrorCode code, String customMessage, Throwable cause) {
        return new AppException(code, customMessage, cause);
    }

    // ── Multiple Errors (validation aggregation) ───────────────────────────

    /**
     * Creates an exception that carries multiple field-level errors.
     *
     * <p>Useful when you want to collect all validation failures at once
     * instead of throwing on the first error. The {@code fieldErrors} map is
     * serialized into the exception message and exposed via
     * {@link MultiErrorException#getFieldErrors()}.
     *
     * <pre>{@code
     * throw ExceptionFactory.withErrors(
     *     ValidationErrorCode.INVALID_INPUT,
     *     Map.of(
     *         "username", "must not be blank",
     *         "email",    "invalid email format"
     *     )
     * );
     * }</pre>
     *
     * @param code        error code describing the overall failure
     * @param fieldErrors map of {@code fieldName → errorMessage}
     */
    public static MultiErrorException withErrors(ErrorCode code, Map<String, String> fieldErrors) {
        return new MultiErrorException(code, fieldErrors);
    }

    /**
     * Creates an exception that carries multiple plain error messages.
     *
     * <pre>{@code
     * throw ExceptionFactory.withErrors(
     *     BusinessErrorCode.BUSINESS_RULE_VIOLATION,
     *     List.of("Stock below safety level", "Supplier lead time exceeded")
     * );
     * }</pre>
     */
    public static MultiErrorException withErrors(ErrorCode code, List<String> errors) {
        return new MultiErrorException(code, errors);
    }
}
