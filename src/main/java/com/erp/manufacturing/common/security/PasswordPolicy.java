package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.MultiErrorException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** best-practices.md S10: min 8 characters with upper, lower, digit and special. */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    /** BCrypt ignores (or rejects) everything past 72 bytes, so a longer password is not really checked. */
    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** @throws MultiErrorException {@code PASSWORD_POLICY} (422) naming the {@code password} field. */
    public static void require(String password) {
        List<String> broken = new ArrayList<>();
        if (password.length() < MIN_LENGTH) broken.add("at least " + MIN_LENGTH + " characters");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) broken.add("at most " + MAX_BYTES + " bytes");
        if (password.chars().noneMatch(Character::isUpperCase)) broken.add("an uppercase letter");
        if (password.chars().noneMatch(Character::isLowerCase)) broken.add("a lowercase letter");
        if (password.chars().noneMatch(Character::isDigit)) broken.add("a digit");
        if (password.chars().allMatch(Character::isLetterOrDigit)) broken.add("a special character");
        if (!broken.isEmpty()) {
            throw ExceptionFactory.withErrors(AuthErrorCode.PASSWORD_POLICY,
                    Map.of("password", "must contain " + String.join(", ", broken)));
        }
    }

    /** Same-as-current is a policy failure on the same field, so the form highlights one place. */
    public static MultiErrorException sameAsCurrent() {
        return ExceptionFactory.withErrors(AuthErrorCode.PASSWORD_POLICY,
                Map.of("password", "must differ from the current password"));
    }
}
