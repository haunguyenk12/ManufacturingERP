package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/** 409 – Account is already active from another IP (strict mode). */
public class SessionConflictException extends AuthException {

    public SessionConflictException(String existingIp) {
        super(ErrorCode.SESSION_CONFLICT, HttpStatus.CONFLICT,
                "Account is already active from IP: " + existingIp + ". Please logout first.");
    }
}
