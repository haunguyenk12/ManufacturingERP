package com.erp.manufacturing.module.workorder.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * G8 (FE Q33): every reason a person types in Giai đoạn 8 — cancelling a work order, an over-BOM or
 * supplemental request, rejecting or withdrawing it — is trimmed and must be 5 to 2000 characters. A missing
 * reason keeps the specific code each command already answered with; one that is there but too short or too
 * long is a {@code VALIDATION_ERROR} on its field.
 */
public final class ReasonRules {

    public static final int MIN = 5;
    public static final int MAX = 2000;

    private ReasonRules() {
    }

    /** @return the trimmed reason, or {@code null} when it is blank */
    public static String lengthChecked(String reason, String field) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() < MIN || trimmed.length() > MAX) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT,
                    Map.of(field, "must be between " + MIN + " and " + MAX + " characters"));
        }
        return trimmed;
    }
}
