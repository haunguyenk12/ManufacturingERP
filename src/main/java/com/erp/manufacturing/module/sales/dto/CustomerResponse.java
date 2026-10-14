package com.erp.manufacturing.module.sales.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID customerId,
        UUID companyId,
        String code,
        String name,
        String contactName,
        String email,
        String phone,
        String address,
        String notes,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
