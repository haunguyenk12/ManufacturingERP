package com.erp.manufacturing.module.organization.service;

import com.erp.manufacturing.module.organization.domain.ScopeResourceType;

import java.util.List;
import java.util.UUID;

public record OrganizationScopeResolution(
        ScopeResourceType scopeType,
        UUID scopeId,
        UUID companyId,
        List<UUID> warehouseIds
) {}
