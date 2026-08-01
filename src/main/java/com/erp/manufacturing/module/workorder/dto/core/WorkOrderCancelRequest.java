package com.erp.manufacturing.module.workorder.dto.core;

/**
 * Body of {@code POST /work-orders/{id}/cancel} (spec §3.2: "cần reason").
 *
 * <p>The reason is required, but it is <b>not</b> annotated {@code @NotBlank}: the service trims it
 * and answers {@code APPROVAL_REASON_REQUIRED} (400), so a blank string and a missing body produce
 * the same specific code instead of a generic {@code VALIDATION_ERROR}. Same shape as
 * {@code ProductionReceiptRejectRequest}.
 */
public record WorkOrderCancelRequest(String reason) {}
