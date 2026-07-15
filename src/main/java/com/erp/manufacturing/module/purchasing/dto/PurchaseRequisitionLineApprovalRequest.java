package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseRequisitionLineApprovalRequest(
        @NotNull UUID purchaseRequisitionLineId,
        @NotNull @Positive BigDecimal approvedQuantity
) {}
