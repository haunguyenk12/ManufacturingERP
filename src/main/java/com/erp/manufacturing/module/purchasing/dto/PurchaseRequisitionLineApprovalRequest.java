package com.erp.manufacturing.module.purchasing.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseRequisitionLineApprovalRequest(
        @NotNull UUID purchaseRequisitionLineId,
        @NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal approvedQuantity
) {}
