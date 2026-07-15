package com.erp.manufacturing.module.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID companyId,
        String code,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
