package com.erp.manufacturing.module.user.domain;

/** User account lifecycle status. */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED,
    /** Created by an admin with an initial password; cannot reach the ERP until the user sets their own. */
    PENDING_SETUP
}
