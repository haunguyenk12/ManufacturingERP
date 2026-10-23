package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * G9b (FE §3.2 {@code contributions[]}): the part of one final-good production execution a receipt claims. Allocated
 * by the server, oldest report first. {@code operationId} is null for a work order without operations.
 */
public record ProductionReceiptContributionResponse(
        UUID executionId,
        String executionCode,
        UUID operationId,
        Integer operationSequence,
        BigDecimal quantity
) {}
