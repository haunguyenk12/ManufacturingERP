package com.erp.manufacturing.module.organization.dto;

import java.util.UUID;

public record AccessScopeResponse(
        UUID scopeId,
        String code,
        String name,
        String scopeType,
        String description,
        String status
) {}
