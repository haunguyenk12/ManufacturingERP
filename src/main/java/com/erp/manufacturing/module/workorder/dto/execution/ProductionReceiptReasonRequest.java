package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * G9a: reject, withdraw and cancel — both versions plus a reason, trimmed and 5–2000 characters (FE Q25). A
 * missing reason is {@code APPROVAL_REASON_REQUIRED}, as rejecting already answered before G9.
 */
public record ProductionReceiptReasonRequest(
        @NotNull Long expectedVersion,
        @NotNull Long expectedWorkOrderVersion,
        @Size(max = 4000) String reason
) {}
