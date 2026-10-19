package com.erp.manufacturing.module.workorder.dto.core;

/**
 * Body of {@code POST /work-orders/{id}/cancel} (spec §3.2: "cần reason").
 *
 * <p>The reason is required, but it is <b>not</b> annotated {@code @NotBlank}: the service trims it
 * and answers {@code APPROVAL_REASON_REQUIRED} (400), so a blank string and a missing body produce
 * the same specific code instead of a generic {@code VALIDATION_ERROR}. Same shape as
 * {@code ProductionReceiptRejectRequest}. {@code expectedWorkOrderVersion} (G8, D5) is required too and
 * checked by the service after the reason, for the same reason.
 */
public record WorkOrderCancelRequest(String reason, Long expectedWorkOrderVersion) {

    /** Shape before G8. */
    public WorkOrderCancelRequest(String reason) {
        this(reason, null);
    }
}
