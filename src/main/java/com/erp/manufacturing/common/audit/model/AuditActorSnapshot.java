package com.erp.manufacturing.common.audit.model;

import java.util.UUID;

/**
 * Who performed the action, snapshotted at the time it happened.
 *
 * <p>{@code username} is a copy on purpose: a user can be renamed, and re-resolving the name at read
 * time would silently rewrite history. {@code userId} is {@code null} for events that happen before
 * anyone is authenticated — a failed login is the whole reason that column is nullable.
 */
public record AuditActorSnapshot(
        UUID userId,
        String username,
        AuditActorType actorType
) {

    public static AuditActorSnapshot user(UUID userId, String username) {
        return new AuditActorSnapshot(userId, username, AuditActorType.USER);
    }

    /** An unauthenticated attempt: we know the name that was offered, nothing more. */
    public static AuditActorSnapshot anonymous(String attemptedUsername) {
        return new AuditActorSnapshot(null, attemptedUsername, AuditActorType.USER);
    }

    public static AuditActorSnapshot system(String componentName) {
        return new AuditActorSnapshot(null, componentName, AuditActorType.SYSTEM);
    }

    public enum AuditActorType {
        USER,
        SYSTEM,
        SERVICE
    }
}
