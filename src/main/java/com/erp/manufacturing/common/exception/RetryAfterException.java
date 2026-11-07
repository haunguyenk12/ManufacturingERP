package com.erp.manufacturing.common.exception;

import lombok.Getter;

/**
 * An {@link AppException} that tells the client how long to wait, surfaced as the
 * {@code Retry-After} response header by {@link GlobalExceptionHandler}. Used where the wait is
 * part of the contract (resend cooldown, exhausted code attempts) rather than a generic rate limit.
 */
@Getter
public class RetryAfterException extends AppException {

    private final long retryAfterSeconds;

    public RetryAfterException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
