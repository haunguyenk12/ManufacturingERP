package com.erp.manufacturing.module.workorder.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** G8c: a quantity summed per id (operation, source execution, issue line…). */
public interface KeyedQuantityProjection {
    UUID getRefId();
    BigDecimal getQuantity();
}
