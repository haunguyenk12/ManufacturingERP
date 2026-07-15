package com.erp.manufacturing.module.planning.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record MrpRunCreateRequest(
        @NotNull UUID companyId,
        @NotNull UUID plantId,
        UUID warehouseId,
        @NotNull LocalDate horizonStartDate,
        @NotNull LocalDate horizonEndDate
) {}
