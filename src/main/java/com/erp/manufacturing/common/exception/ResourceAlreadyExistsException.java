package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/** 409 – Resource already exists (unique constraint violation). */
public class ResourceAlreadyExistsException extends BaseBusinessException {

    public ResourceAlreadyExistsException(String message) {
        super(ErrorCode.RESOURCE_ALREADY_EXISTS, HttpStatus.CONFLICT, message);
    }
}
