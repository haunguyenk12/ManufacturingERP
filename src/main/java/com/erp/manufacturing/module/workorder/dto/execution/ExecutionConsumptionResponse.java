package com.erp.manufacturing.module.workorder.dto.execution;

import java.math.BigDecimal;
import java.util.UUID;

/** G8c: one recorded consumption of an execution, per issue line. */
public record ExecutionConsumptionResponse(UUID issueLineId, BigDecimal quantity) {}
