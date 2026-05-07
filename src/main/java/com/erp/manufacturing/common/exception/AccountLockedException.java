package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends AuthException {
    public AccountLockedException(String message) {
        super(ErrorCode.ACCOUNT_LOCKED, HttpStatus.FORBIDDEN, message);
    }
}
