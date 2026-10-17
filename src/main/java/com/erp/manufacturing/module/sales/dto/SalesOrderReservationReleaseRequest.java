package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Release one hold (G6a). {@code reason} goes to the audit trail only. */
public record SalesOrderReservationReleaseRequest(
        @NotNull Long expectedVersion,
        @Size(max = 2000) String reason
) {}
