package com.erp.manufacturing.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Root of the custom business exception hierarchy.
 * All domain exceptions extend this class.
 */
@Getter
public abstract class BaseBusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    protected BaseBusinessException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BaseBusinessException(ErrorCode errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
