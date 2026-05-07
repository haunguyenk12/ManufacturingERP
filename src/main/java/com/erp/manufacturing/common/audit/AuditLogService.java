package com.erp.manufacturing.common.audit;

import com.erp.manufacturing.common.context.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Facade for publishing audit log events.
 * Business code should call this service; it decouples from storage details.
 * All methods are fire-and-forget (async via Spring Events).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ApplicationEventPublisher eventPublisher;

    // ── Standard request-scoped logging ──────────────────────────────────

    public void log(RequestContext ctx, AuditAction action) {
        publish(ctx, action, null, null, null, "SUCCESS");
    }

    public void log(RequestContext ctx, AuditAction action, String description) {
        publish(ctx, action, null, null, description, "SUCCESS");
    }

    public void logEntity(RequestContext ctx, AuditAction action,
                           String entityType, Object entityId) {
        publish(ctx, action, entityType,
                entityId != null ? entityId.toString() : null, null, "SUCCESS");
    }

    public void logFailure(RequestContext ctx, AuditAction action, String reason) {
        publish(ctx, action, null, null, reason, "FAILURE");
    }

    // ── Auth events (may not have full request context) ───────────────────

    public void logAuth(UUID userId, String username, String ip, String traceId,
                         AuditAction action, String description) {
        RequestContext ctx = RequestContext.forAuth(userId, username, ip, traceId);
        publish(ctx, action, null, null, description, "SUCCESS");
    }

    public void logAuthFailure(String username, String ip, String traceId,
                                AuditAction action, String reason) {
        RequestContext ctx = RequestContext.forAuth(null, username, ip, traceId);
        publish(ctx, action, null, null, reason, "FAILURE");
    }

    // ── Private ───────────────────────────────────────────────────────────

    private void publish(RequestContext ctx, AuditAction action,
                          String entityType, String entityId,
                          String description, String status) {
        eventPublisher.publishEvent(
                new AuditLogEvent(ctx, action.name(), entityType, entityId, description, status));
    }
}
