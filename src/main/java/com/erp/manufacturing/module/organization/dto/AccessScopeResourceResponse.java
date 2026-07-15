package com.erp.manufacturing.module.organization.dto;

import java.util.UUID;

public record AccessScopeResourceResponse(
        UUID scopeResourceId,
        UUID scopeId,
        String resourceType,
        UUID resourceId
) {}
