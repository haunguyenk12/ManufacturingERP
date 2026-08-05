package com.erp.manufacturing.module.shift.dto;

import java.time.LocalTime;
import java.util.UUID;

public record ShiftBreakResponse(
        UUID shiftBreakId,
        LocalTime startTime,
        LocalTime endTime
) {}
