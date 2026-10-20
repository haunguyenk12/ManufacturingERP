package com.erp.manufacturing.module.workorder.domain;

import java.math.BigDecimal;

/**
 * G9a (FE §3.1): the output quota of one work order, in its output unit. One definition for the create/edit
 * ceiling, the approval re-check and every read model that shows the numbers (B226).
 *
 * <ul>
 *   <li>{@code G} — final good: the good of the last operation, retries included ({@code actualGoodQuantity}, B219);</li>
 *   <li>{@code C = min(P, G)} — D12: good beyond the plan is never receipted;</li>
 *   <li>{@code A} — approved receipts ({@code completedQuantity});</li>
 *   <li>{@code D} — DRAFT and PENDING_APPROVAL receipts (B16).</li>
 * </ul>
 * {@code A ≤ G ≤ P} is a database invariant ({@code chk_work_orders_completed_quantity} V14,
 * {@code chk_work_orders_actual_good} and {@code chk_work_orders_completed_within_good} V32), so {@code C} is {@code G} in
 * practice and none of the differences below can go negative; the clamps only spell out the FE formula.
 */
public record ReceiptQuota(BigDecimal planned, BigDecimal finalGood, BigDecimal approved, BigDecimal claimed) {

    public static ReceiptQuota of(WorkOrder workOrder, BigDecimal claimed) {
        return new ReceiptQuota(workOrder.getPlannedQuantity(), workOrder.getActualGoodQuantity(),
                workOrder.getCompletedQuantity(), claimed == null ? BigDecimal.ZERO : claimed);
    }

    public BigDecimal cap() {
        return finalGood.min(planned);
    }

    /** W = max(0, C − A). */
    public BigDecimal awaiting() {
        return cap().subtract(approved).max(BigDecimal.ZERO);
    }

    /** U = max(0, C − A − D). */
    public BigDecimal unclaimed() {
        return cap().subtract(approved).subtract(claimed).max(BigDecimal.ZERO);
    }

    /** What one receipt may claim when it is itself among the claims: its own quantity is given back first. */
    public BigDecimal unclaimedExcluding(BigDecimal ownClaim) {
        return cap().subtract(approved).subtract(claimed.subtract(ownClaim)).max(BigDecimal.ZERO);
    }
}
