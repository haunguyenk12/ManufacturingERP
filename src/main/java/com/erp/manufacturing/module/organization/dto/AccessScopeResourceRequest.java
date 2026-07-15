package com.erp.manufacturing.module.organization.dto;

import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AccessScopeResourceRequest(
        @NotNull
        ScopeResourceType resourceType,

        @NotNull
        UUID resourceId
) {}
