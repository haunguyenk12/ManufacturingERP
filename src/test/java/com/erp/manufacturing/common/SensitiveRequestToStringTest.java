package com.erp.manufacturing.common;

import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.user.dto.CreateUserRequest;
import com.erp.manufacturing.module.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards technical debt #7: every request DTO is a {@code record}, and the compiler-generated
 * {@code toString()} prints the value of every component. Any log line or exception message that
 * carries one of these objects would therefore write a credential to disk
 * (see {@code .claude/rules/best-practices.md} S14, {@code common/security/CLAUDE.md} §4.18).
 *
 * <p>The invariant is cross-module (auth + user), which is why the test lives in {@code common}
 * rather than beside either DTO package.
 */
@DisplayName("Sensitive request DTOs never expose secrets through toString()")
class SensitiveRequestToStringTest {

    private static final String RAW_PASSWORD = "SuperSecret123!";
    private static final String RAW_REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.refresh-token-payload.signature";

    @Test
    @DisplayName("LoginRequest hides the password but keeps username and deviceId readable")
    void loginRequest_hidesPasswordOnly() {
        String rendered = new LoginRequest("operator1", RAW_PASSWORD, "device-01").toString();

        assertThat(rendered).doesNotContain(RAW_PASSWORD);
        // A toString() that hides everything is useless for debugging, and a test that only asserts
        // absence would still pass if someone replaced the whole body with "".
        assertThat(rendered).contains("operator1", "device-01", "***");
    }

    @Test
    @DisplayName("RefreshRequest hides the refresh token but keeps tokenId readable")
    void refreshRequest_hidesRefreshTokenButNotTokenId() {
        String rendered = new RefreshRequest(RAW_REFRESH_TOKEN, "token-id-42", "device-01").toString();

        assertThat(rendered).doesNotContain(RAW_REFRESH_TOKEN);
        // tokenId is a lookup key for the Redis session, not a credential on its own.
        assertThat(rendered).contains("token-id-42", "device-01", "***");
    }

    @Test
    @DisplayName("LogoutRequest hides the refresh token")
    void logoutRequest_hidesRefreshToken() {
        String rendered = new LogoutRequest(RAW_REFRESH_TOKEN, "token-id-42").toString();

        assertThat(rendered).doesNotContain(RAW_REFRESH_TOKEN);
        assertThat(rendered).contains("token-id-42", "***");
    }

    @Test
    @DisplayName("CreateUserRequest hides the password but keeps username and email readable")
    void createUserRequest_hidesPassword() {
        String rendered = new CreateUserRequest("operator1", "operator1@erp.local", RAW_PASSWORD).toString();

        assertThat(rendered).doesNotContain(RAW_PASSWORD);
        assertThat(rendered).contains("operator1", "operator1@erp.local", "***");
    }

    @Test
    @DisplayName("UpdateUserRequest hides a supplied password and still shows when none was sent")
    void updateUserRequest_hidesPasswordAndKeepsAbsenceVisible() {
        assertThat(new UpdateUserRequest("operator1@erp.local", RAW_PASSWORD).toString())
                .doesNotContain(RAW_PASSWORD)
                .contains("operator1@erp.local", "***");

        // "no password sent" and "password hidden" lead to different investigations, so the masking
        // must not collapse them into the same output.
        assertThat(new UpdateUserRequest("operator1@erp.local", null).toString())
                .contains("password=null")
                .doesNotContain("***");
    }
}
