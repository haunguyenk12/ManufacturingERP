package com.erp.manufacturing.module.purchasing.dto;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID supplierId,
        UUID companyId,
        String companyCode,
        String code,
        String name,
        String email,
        String phone,
        String address,
        String taxCode,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
