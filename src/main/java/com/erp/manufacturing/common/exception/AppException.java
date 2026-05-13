package com.erp.manufacturing.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Root of the custom exception hierarchy.
 *
 * <p>Holds an {@link ErrorCode} (interface), an HTTP status override, and an optional
 * custom message that overrides the code's default message.
 *
 * <p>Direct instantiation is discouraged – use {@link ExceptionFactory} instead.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode  errorCode;
    private final HttpStatus httpStatus;

    /**
     * Creates an exception using the error code's default message and status.
     */
    public AppException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode  = errorCode;
        this.httpStatus = errorCode.status();
    }

    /**
     * Creates an exception with a custom message, overriding the code's default.
     */
    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode  = errorCode;
        this.httpStatus = errorCode.status();
    }

    /**
     * Creates an exception with a custom HTTP status (e.g. downgrade 500 → 503).
     */
    public AppException(ErrorCode errorCode, HttpStatus httpStatus, String customMessage) {
        super(customMessage);
        this.errorCode  = errorCode;
        this.httpStatus = httpStatus;
    }

    /**
     * Creates an exception wrapping a root cause.
     */
    public AppException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode  = errorCode;
        this.httpStatus = errorCode.status();
    }

    /** Convenience: error code string for logging. */
    public String getCode() {
        return errorCode.code();
    }
}
