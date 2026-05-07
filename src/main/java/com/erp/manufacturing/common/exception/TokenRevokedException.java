package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

public class TokenRevokedException extends AuthException {
    public TokenRevokedException() {
        super(ErrorCode.TOKEN_REVOKED, HttpStatus.UNAUTHORIZED, "Token has been revoked");
    }
}
