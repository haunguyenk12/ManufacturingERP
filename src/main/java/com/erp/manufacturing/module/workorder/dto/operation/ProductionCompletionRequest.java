package com.erp.manufacturing.module.workorder.dto.operation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * G8c (FE R14, D6): complete production of a work order by command. {@code reason} is required — 5 to 2000
 * characters — only when the final good is below the plan.
 */
public record ProductionCompletionRequest(
        @NotNull Long expectedWorkOrderVersion,
        @Size(max = 2000) String reason
) {}
