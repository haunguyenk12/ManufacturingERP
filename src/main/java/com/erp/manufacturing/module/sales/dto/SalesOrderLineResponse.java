package com.erp.manufacturing.module.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesOrderLineResponse(
        UUID salesOrderLineId,
        Integer lineNo,
        UUID itemId,
        String itemSku,
        String itemName,
        String uom,
        BigDecimal orderedQuantity,
        BigDecimal fulfilledQuantity,
        BigDecimal openQuantity,
        LocalDate dueDate
) {}
