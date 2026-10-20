package com.erp.manufacturing.module.workorder.dto.execution;

import jakarta.validation.constraints.NotNull;

/** G9a (FE R19): the receipt and work order versions the client last read, on submit and approve. */
public record ProductionReceiptVersionRequest(
        @NotNull Long expectedVersion,
        @NotNull Long expectedWorkOrderVersion
) {}
