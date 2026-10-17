package com.erp.manufacturing.module.sales.dto;

import com.erp.manufacturing.common.response.PageResult;

import java.math.BigDecimal;
import java.util.UUID;

/** Positions for one line, plus how much of the line can still be held ({@code uncoveredQuantity}). */
public record SalesOrderAllocationCandidatesResponse(
        UUID salesOrderLineId,
        UUID itemId,
        String uom,
        BigDecimal uncoveredQuantity,
        PageResult<SalesOrderAllocationCandidateResponse> positions
) {}
