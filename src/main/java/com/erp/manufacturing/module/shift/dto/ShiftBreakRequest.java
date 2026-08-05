package com.erp.manufacturing.module.shift.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ShiftBreakRequest(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {}
