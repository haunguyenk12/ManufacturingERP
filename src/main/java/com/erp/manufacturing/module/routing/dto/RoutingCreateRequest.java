package com.erp.manufacturing.module.routing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Operations are supplied inline: a routing without operations can never be activated, so there is
 * no useful intermediate state that would justify separate line endpoints (unlike BOM, whose lines
 * are edited independently).
 */
public record RoutingCreateRequest(
        @NotNull UUID itemId,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 40) String version,
        @Size(max = 2000) String note,
        @NotEmpty List<@Valid RoutingOperationRequest> operations
) {}
