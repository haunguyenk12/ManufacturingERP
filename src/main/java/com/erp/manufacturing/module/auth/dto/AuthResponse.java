package com.erp.manufacturing.module.auth.dto;

/**
 * Auth response returned on successful login or token refresh.
 *
 * <ul>
 *   <li>{@code expiresIn}    – access token TTL in seconds</li>
 *   <li>{@code deviceId}     – the effective device ID registered for this session
 *                              (echoed back so client can persist it)</li>
 *   <li>{@code sessionKicked} – true if a previous session from another IP was terminated on login</li>
 * </ul>
 */
public record AuthResponse(
        String  accessToken,
        String  refreshToken,
        String  tokenId,
        long    expiresIn,
        String  deviceId,
        boolean sessionKicked
) {}
