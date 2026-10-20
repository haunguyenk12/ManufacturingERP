package com.erp.manufacturing.module.workorder.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** G8d: rework recorded on a work order and how much of it retries already processed. */
public interface WorkOrderReworkTotals {
    UUID getWorkOrderId();
    BigDecimal getRework();
    BigDecimal getRetryProcessed();
}
