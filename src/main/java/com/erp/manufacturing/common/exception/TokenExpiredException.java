package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends AuthException {
    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Access token has expired");
    }
}
