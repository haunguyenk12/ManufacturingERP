package com.erp.manufacturing.module.workorder.dto.execution;

import com.erp.manufacturing.module.workorder.domain.QualityDispositionResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductionReceiptQcDispositionRequest(
        @NotNull QualityDispositionResult result,
        @NotBlank @Size(max = 1000) String reason
) {}
