package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles 401 Unauthorized responses using the unified ApiResponse format.
 *
 * <p>This is reached only when no credentials were presented at all: {@link JwtAuthenticationFilter}
 * either sets a full {@code Authentication} on success, or answers a *presented* (malformed/expired)
 * token directly and returns before Spring Security's authorization stage ever runs — so this handler
 * only ever sees the "nothing was sent" case, never a bad token. Uses {@link
 * AuthErrorCode#AUTHENTICATION_REQUIRED}, not {@link AuthErrorCode#TOKEN_MALFORMED} — the two describe
 * different situations and a client needs to be able to branch on which one happened.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(AuthErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required to access this resource"));
    }
}
