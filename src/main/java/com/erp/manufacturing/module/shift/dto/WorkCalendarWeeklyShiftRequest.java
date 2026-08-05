package com.erp.manufacturing.module.shift.dto;

import com.erp.manufacturing.module.shift.domain.Weekday;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WorkCalendarWeeklyShiftRequest(
        @NotNull Weekday weekday,
        @NotNull UUID shiftId
) {}
