package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/** Base for all authentication/token-related exceptions. */
public abstract class AuthException extends BaseBusinessException {

    protected AuthException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
}
