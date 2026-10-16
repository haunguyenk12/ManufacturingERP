package com.erp.manufacturing.module.shift.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ShiftResponse(
        UUID shiftId,
        UUID plantId,
        String code,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        List<ShiftBreakResponse> breaks,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        /** Window minus breaks, per occurrence (G5). Server-computed, never accepted from a client. */
        Integer workingMinutes
) {}
