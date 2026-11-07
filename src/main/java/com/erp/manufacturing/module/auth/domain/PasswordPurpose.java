package com.erp.manufacturing.module.auth.domain;

/** Why a password challenge was issued. */
public enum PasswordPurpose {
    /** First login of an account an admin created with an initial password. */
    SETUP,
    /** Public forgot-password recovery. */
    RESET
}
