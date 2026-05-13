package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Contract for all error codes in the system.
 *
 * <p>Each domain defines its own {@code enum} implementing this interface,
 * grouping related errors together. This replaces the single flat enum approach
 * and allows each module to own its error definitions independently.
 *
 * <h3>Convention</h3>
 * <ul>
 *   <li>{@link #code()}    – machine-readable string sent in API response ({@code "AUTH_001"})</li>
 *   <li>{@link #message()} – default human-readable message (overridable per throw-site)</li>
 *   <li>{@link #status()}  – default HTTP status for this error</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * throw ExceptionFactory.notFound(AuthErrorCode.USER_NOT_FOUND);
 * throw ExceptionFactory.alreadyExists(AuthErrorCode.USERNAME_TAKEN, "admin");
 * }</pre>
 */
public interface ErrorCode {

    /** Machine-readable code sent to frontend. E.g. {@code "AUTH_001"}. */
    String code();

    /** Default human-readable message for this error. */
    String message();

    /** Default HTTP status associated with this error. */
    HttpStatus status();
}
