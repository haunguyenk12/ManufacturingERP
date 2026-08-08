package com.erp.manufacturing.module.sales.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderResponse(
        UUID salesOrderId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        String orderNo,
        String customerName,
        LocalDate orderDate,
        String status,
        String note,
        Instant createdAt,
        Instant updatedAt,
        Long version,
        List<SalesOrderLineResponse> lines
) {}
