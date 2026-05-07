package com.erp.manufacturing.common.response;

import com.erp.manufacturing.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Universal response envelope for ALL API responses (success AND error).
 * <p>
 * Contract: {@code { code, result, message }}
 * <ul>
 *   <li>{@code code}    – Always present. {@link ErrorCode} name. Frontend branches on this.</li>
 *   <li>{@code result}  – Data payload when success; {@code null} on error.</li>
 *   <li>{@code message} – Human-readable text. Always present.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        T result,
        String message
) {

    // ── Success factories ──────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T result) {
        return new ApiResponse<>(ErrorCode.SUCCESS.name(), result, "OK");
    }

    public static <T> ApiResponse<T> ok(T result, String message) {
        return new ApiResponse<>(ErrorCode.SUCCESS.name(), result, message);
    }

    public static <T> ApiResponse<T> created(T result) {
        return new ApiResponse<>(ErrorCode.SUCCESS.name(), result, "Created successfully");
    }

    public static <T> ApiResponse<T> noContent(String message) {
        return new ApiResponse<>(ErrorCode.SUCCESS.name(), null, message);
    }

    // ── Error factories ────────────────────────────────────────────────────

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.name(), null, message);
    }
}
