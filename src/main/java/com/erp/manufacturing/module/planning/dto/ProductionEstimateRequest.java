package com.erp.manufacturing.module.planning.dto;

import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionEstimateRequest(
        @NotNull UUID productItemId,
        @NotNull ScopeResourceType scopeType,
        @NotNull UUID scopeId,
        @NotNull @Positive BigDecimal targetQuantity
) {}
