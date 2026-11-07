package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("PasswordPolicy")
class PasswordPolicyTest {

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = {"Str0ng!Passw0rd", "Aa1!aaaa", "Mật-khẩu-1-Mạnh"})
    void accepts(String password) {
        assertThatCode(() -> PasswordPolicy.require(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"Sh0rt!a", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!!", "NoSpecial1Chars", "        ", ""})
    void rejects(String password) {
        MultiErrorException ex = catchThrowableOfType(() -> PasswordPolicy.require(password), MultiErrorException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORD_POLICY);
        assertThat(ex.getFieldErrors()).containsKey("password");
    }

    @Test
    @DisplayName("more than 72 bytes is refused because BCrypt would not look past them")
    void rejectsOver72Bytes() {
        String tooLong = "Aa1!" + "x".repeat(70);

        assertThat(catchThrowableOfType(() -> PasswordPolicy.require(tooLong), MultiErrorException.class).getFieldErrors().get("password"))
                .contains("72 bytes");
    }

    @Test
    @DisplayName("names every missing rule, not just the first")
    void namesAllBrokenRules() {
        String message = catchThrowableOfType(() -> PasswordPolicy.require("abc"), MultiErrorException.class)
                .getFieldErrors().get("password");

        assertThat(message).contains("at least 8", "uppercase", "digit", "special");
        assertThat(message).doesNotContain("lowercase");
    }
}
