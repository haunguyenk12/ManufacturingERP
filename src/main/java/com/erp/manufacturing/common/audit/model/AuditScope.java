package com.erp.manufacturing.common.audit.model;

import java.util.UUID;

/**
 * Organisational scope the audited event belongs to (AR-3).
 *
 * <p>{@code audit_logs.plant_id} has existed since {@code V54} and has been {@code null} on every row
 * ever written, so the {@code plantId} filter on the read API has always been a correct no-op. The
 * fix is not a backfill: guessing a historical row's plant from today's data would falsify the
 * snapshot. New events populate it from the command itself.
 *
 * <p><strong>Resolution order</strong> (enforced by {@code AuditRecorder}): an explicit value from
 * the annotation or a descriptor wins; then a value derived from the entity snapshot; then the
 * caller's authenticated scope — and only that, never the {@code X-Plant-Id} header on its own, since
 * a header the client controls is not evidence of where the change landed. If none of those answer,
 * the field stays {@code null}, which is honest, rather than being guessed.
 */
public record AuditScope(
        UUID companyId,
        UUID plantId,
        UUID warehouseId
) {

    public static final AuditScope EMPTY = new AuditScope(null, null, null);

    public static AuditScope of(UUID companyId, UUID plantId, UUID warehouseId) {
        return new AuditScope(companyId, plantId, warehouseId);
    }

    public static AuditScope ofPlant(UUID plantId) {
        return new AuditScope(null, plantId, null);
    }

    public static AuditScope ofCompany(UUID companyId) {
        return new AuditScope(companyId, null, null);
    }

    public boolean isEmpty() {
        return companyId == null && plantId == null && warehouseId == null;
    }

    /** Fills only the fields this scope leaves blank; never overwrites a more specific answer. */
    public AuditScope mergedWith(AuditScope fallback) {
        if (fallback == null) {
            return this;
        }
        return new AuditScope(
                companyId != null ? companyId : fallback.companyId(),
                plantId != null ? plantId : fallback.plantId(),
                warehouseId != null ? warehouseId : fallback.warehouseId());
    }
}
