package com.erp.manufacturing.module.sales.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection for {@link SalesOrderLineRepository#findEligiblePlanningDemands}. Keeps the
 * repository free of DTO types while still returning everything the planning screen needs in a
 * single query (rule {@code C14}).
 */
public interface SalesOrderPlanningDemandProjection {

    UUID getSalesOrderId();

    String getSalesOrderCode();

    UUID getSalesOrderLineId();

    Integer getLineNo();

    UUID getItemId();

    String getItemSku();

    String getItemName();

    String getUom();

    BigDecimal getOpenQuantity();

    LocalDate getDueDate();
}
