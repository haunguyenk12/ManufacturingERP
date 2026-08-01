package com.erp.manufacturing.module.planning.domain;

/**
 * Reason codes attached to a supply suggestion, explaining its {@link SupplySuggestionExceptionState}
 * (spec §8.1).
 *
 * <p>{@link #MISSING_BOM} and {@link #MISSING_ROUTING} intentionally reuse the exact strings of the
 * {@code BusinessErrorCode} constants of the same name (F4/F5-A) — the same condition reported as a
 * planning message here and as an error code when a convert is attempted must not have two names.
 *
 * <p>{@code PURCHASING_DEFERRED} from the spec is not modelled: it exists because purchasing is out
 * of the frontend MVP, while this backend has a full purchasing module a BUY proposal converts into.
 */
public enum PlanningMessageCode {
    MATERIAL_SHORTAGE,
    MISSING_BOM,
    MISSING_ROUTING,
    SYSTEM_FALLBACK_USED
}
