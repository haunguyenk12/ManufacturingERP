package com.erp.manufacturing.common.response;

/**
 * One field-level validation error inside {@link ApiResponse#errors()}.
 *
 * <p>Contract: {@code { "field": "quantity", "message": "Maximum allowed is 5 PCS" }}
 */
public record FieldErrorResponse(String field, String message) {
}
