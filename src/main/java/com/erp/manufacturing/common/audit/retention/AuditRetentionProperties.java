package com.erp.manufacturing.common.audit.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retention policy for the audit trail ({@code app.audit.retention.*}).
 *
 * <p><strong>Disabled by default, deliberately.</strong> How long an audit trail must be kept online
 * is a legal and business question, not an engineering default (AuditRefactorPlan §11.2 lists it as
 * an open decision with a named approver). Shipping this switched on would mean the first deployment
 * quietly destroys records under a number this file invented. It stays off until an owner sets both
 * the retention window and {@code enabled} explicitly.
 *
 * @param enabled         master switch; purging never happens while this is false
 * @param retentionMonths how long rows stay online, counted from the event time
 * @param batchSize       rows deleted per transaction, so a purge cannot hold one long lock
 * @param archiveRequired refuse to delete unless an archive has been confirmed for the window
 */
@ConfigurationProperties(prefix = "app.audit.retention")
public record AuditRetentionProperties(
        Boolean enabled,
        Integer retentionMonths,
        Integer batchSize,
        Boolean archiveRequired
) {

    public AuditRetentionProperties {
        enabled = enabled != null && enabled;
        retentionMonths = retentionMonths == null || retentionMonths < 1 ? 84 : retentionMonths;
        batchSize = batchSize == null || batchSize < 1 ? 1000 : batchSize;
        archiveRequired = archiveRequired == null || archiveRequired;
    }
}
