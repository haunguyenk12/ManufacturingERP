package com.erp.manufacturing.module.routing.dto;

import jakarta.validation.constraints.NotNull;

/** Body of activate/deactivate (G5). Activate also accepts no body at all, as before G5. */
public record RoutingVersionRequest(@NotNull Long expectedVersion) {}
