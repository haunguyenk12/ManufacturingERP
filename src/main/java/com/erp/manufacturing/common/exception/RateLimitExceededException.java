package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/** 429 – Client exceeded configured rate limit. */
public class RateLimitExceededException extends BaseBusinessException {

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
