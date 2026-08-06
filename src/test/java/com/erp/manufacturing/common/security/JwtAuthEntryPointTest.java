package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0 auth fix: {@link JwtAuthEntryPoint} used to hardcode {@code TOKEN_MALFORMED} for every
 * unauthenticated request, including this one — the only case it can actually be reached for (no
 * credentials presented at all; a *presented* bad token is caught and answered directly inside
 * {@link JwtAuthenticationFilter}, never reaching here). It now uses the accurate
 * {@link AuthErrorCode#AUTHENTICATION_REQUIRED} instead.
 */
@DisplayName("JwtAuthEntryPoint tests")
class JwtAuthEntryPointTest {

    private final JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint(new ObjectMapper());

    @Test
    @DisplayName("commence – writes 401 AUTHENTICATION_REQUIRED, not TOKEN_MALFORMED")
    void commence_writesAuthenticationRequired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("no credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AuthErrorCode.AUTHENTICATION_REQUIRED.code());
        assertThat(response.getContentAsString()).doesNotContain(AuthErrorCode.TOKEN_MALFORMED.code());
    }
}
