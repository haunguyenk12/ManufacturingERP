package com.erp.manufacturing.module.workorder.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** G8c: what the executions of one operation recorded, split into fresh work and retries of rework. */
public interface OperationExecutionTotals {
    UUID getOperationId();
    BigDecimal getGood();
    BigDecimal getScrap();
    BigDecimal getRework();
    BigDecimal getFreshProcessed();
    BigDecimal getRetryProcessed();
    Long getExecutionCount();
}
