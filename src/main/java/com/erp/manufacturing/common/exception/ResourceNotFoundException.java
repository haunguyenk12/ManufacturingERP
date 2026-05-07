package com.erp.manufacturing.common.exception;

import org.springframework.http.HttpStatus;

/** 404 – Requested resource does not exist. */
public class ResourceNotFoundException extends BaseBusinessException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                resourceName + " not found with id: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
}
