package com.erp.manufacturing.common.exception;

import lombok.Getter;

/**
 * An {@link AppException} whose error envelope also carries a {@code result}.
 *
 * <p>The envelope contract says {@code result} is absent on error. This is the one deliberate
 * exception: when a failure has <em>changed</em> the resource (G3 — a commit found the batch stale
 * and flipped it to {@code INVALID}), withholding the new state would force the client into a second
 * request just to learn what the failure was about. Use it only for that shape of failure.
 */
@Getter
public class ResultCarryingException extends AppException {

    private final transient Object result;

    public ResultCarryingException(ErrorCode errorCode, String message, Object result) {
        super(errorCode, message);
        this.result = result;
    }
}
