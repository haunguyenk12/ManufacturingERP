package com.erp.manufacturing.common.audit.model;

/**
 * Transport-level facts about the call that produced the event.
 *
 * <p>All five values are already capped by {@code AuditInputSanitizer} before a snapshot is built, so
 * nothing downstream has to defend against a caller-supplied header again.
 *
 * <p>{@code traceId} correlates the event with application logs. It is deliberately <em>not</em> the
 * same concept as the {@code traceId} column persisted on business documents — see
 * {@code .claude/rules/error-handling.md §5.1}, which keeps those two apart on purpose.
 */
public record AuditRequestSnapshot(
        String traceId,
        String clientIp,
        String userAgent,
        String httpMethod,
        String requestPath
) {

    public static final AuditRequestSnapshot EMPTY =
            new AuditRequestSnapshot(null, null, null, null, null);

    public static AuditRequestSnapshot ofTrace(String traceId, String clientIp) {
        return new AuditRequestSnapshot(traceId, clientIp, null, null, null);
    }
}
