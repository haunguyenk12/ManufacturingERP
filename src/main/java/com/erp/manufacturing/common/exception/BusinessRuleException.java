package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/** 422 – Business rule violated (domain-level validation). */
public class BusinessRuleException extends BaseBusinessException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessRuleException(ErrorCode code, String message) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
