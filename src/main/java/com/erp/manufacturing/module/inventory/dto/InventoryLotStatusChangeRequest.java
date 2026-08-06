package com.erp.manufacturing.module.inventory.dto;

import com.erp.manufacturing.module.inventory.domain.LotStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code POST /inventory/lots/{lotId}/status} (C2-2). {@code warehouseId} is required — a lot can
 * hold stock in more than one warehouse, and {@link com.erp.manufacturing.module.inventory.service.
 * LotStatusChangeCommand} needs one to resolve the {@code StockBalance} row the ledger entry is
 * recorded against. {@code reason} is required (unlike the generic receive/issue/adjust endpoints,
 * where it is optional) because the spec calls this out explicitly for status mutation.
 * {@code newStatus} is validated at the service layer to be one of {@code AVAILABLE}/{@code HOLD}/
 * {@code REJECTED} — {@code EXPIRED} has no established manual-transition flow yet.
 */
public record InventoryLotStatusChangeRequest(
        @NotNull UUID warehouseId,
        @NotNull LotStatus newStatus,
        @NotBlank @Size(max = 1000) String reason,
        @Size(max = 80) String referenceType,
        @Size(max = 120) String referenceId
) {}
