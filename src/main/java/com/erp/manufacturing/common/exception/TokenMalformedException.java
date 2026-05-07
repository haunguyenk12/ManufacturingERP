package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

public class TokenMalformedException extends AuthException {
    public TokenMalformedException() {
        super(ErrorCode.TOKEN_MALFORMED, HttpStatus.UNAUTHORIZED, "Token is malformed or invalid");
    }
}
