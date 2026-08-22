package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record MaterialIssueLineRequest(
        @NotNull UUID componentLineId,
        UUID reservationId,
        @NotNull UUID warehouseId,
        UUID lotId,
        @Size(max = 120) String lotNumber,
        @Schema(hidden = true) UUID serialId,
        @NotNull @Positive BigDecimal quantity,
        @Size(max = 1000) String reason,
        /** Required when quantity exceeds the component's remaining BOM requirement. */
        @Size(max = 1000) String overrideReason,
        MaterialIssueReasonCode reasonCode,
        UUID sourceExecutionId
) {
    public MaterialIssueLineRequest(UUID componentLineId, UUID reservationId, UUID warehouseId,
                                    UUID lotId, String lotNumber, UUID serialId, BigDecimal quantity,
                                    String reason, String overrideReason) {
        this(componentLineId, reservationId, warehouseId, lotId, lotNumber, serialId, quantity,
                reason, overrideReason, null, null);
    }
}
