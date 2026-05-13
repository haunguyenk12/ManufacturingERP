package com.erp.manufacturing.common.response;

import com.erp.manufacturing.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Universal response envelope for ALL API responses (success AND error).
 *
 * <p>Contract: {@code { code, result, message, errors }}
 * <ul>
 *   <li>{@code code}    – Always present. Machine-readable error code (e.g. {@code "AUTH_001"}). Frontend branches on this.</li>
 *   <li>{@code result}  – Data payload on success; {@code null} on error.</li>
 *   <li>{@code message} – Human-readable text. Always present.</li>
 *   <li>{@code errors}  – Present only for multi-error responses (field-level validation).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String              code,
        T                   result,
        String              message,
        Map<String, String> errors
) {
    private static final String SUCCESS_CODE = "SUCCESS";

    // ── Success factories ──────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T result) {
        return new ApiResponse<>(SUCCESS_CODE, result, "OK", null);
    }

    public static <T> ApiResponse<T> ok(T result, String message) {
        return new ApiResponse<>(SUCCESS_CODE, result, message, null);
    }

    public static <T> ApiResponse<T> created(T result) {
        return new ApiResponse<>(SUCCESS_CODE, result, "Created successfully", null);
    }

    public static <T> ApiResponse<T> noContent(String message) {
        return new ApiResponse<>(SUCCESS_CODE, null, message, null);
    }

    // ── Error factories ────────────────────────────────────────────────────

    /** Single error – uses code's string and a custom message. */
    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.code(), null, message, null);
    }

    /** Single error – uses both code string and code's default message. */
    public static <T> ApiResponse<T> error(ErrorCode code) {
        return new ApiResponse<>(code.code(), null, code.message(), null);
    }

    /** Multi-field error (for validation aggregation). */
    public static <T> ApiResponse<T> fieldErrors(ErrorCode code, String message,
                                                  Map<String, String> fieldErrors) {
        return new ApiResponse<>(code.code(), null, message, fieldErrors);
    }

    /** Multi-error using a list of plain messages (joined). */
    public static <T> ApiResponse<T> multiErrors(ErrorCode code, List<String> errorList) {
        String message = String.join("; ", errorList);
        return new ApiResponse<>(code.code(), null, message, null);
    }
}
