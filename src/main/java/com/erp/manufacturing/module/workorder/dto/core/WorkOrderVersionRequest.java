package com.erp.manufacturing.module.workorder.dto.core;

import jakarta.validation.constraints.NotNull;

/** Body of a work order command that needs nothing but the version last read (G8, D5). */
public record WorkOrderVersionRequest(@NotNull Long expectedWorkOrderVersion) {}
