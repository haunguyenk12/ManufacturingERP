package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenExpiredException extends AuthException {
    public RefreshTokenExpiredException() {
        super(ErrorCode.REFRESH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Refresh token has expired. Please login again.");
    }
}
