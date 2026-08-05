package com.erp.manufacturing.module.shift.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkCalendarResponse(
        UUID workCalendarId,
        UUID plantId,
        String code,
        String name,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        List<WorkCalendarWeeklyShiftResponse> weeklyShifts,
        List<WorkCalendarExceptionResponse> exceptions,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}
