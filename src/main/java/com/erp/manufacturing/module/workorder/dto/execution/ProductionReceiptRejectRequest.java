package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductionReceiptRejectRequest(
        @NotBlank @Size(max = 1000) String reason
) {}
