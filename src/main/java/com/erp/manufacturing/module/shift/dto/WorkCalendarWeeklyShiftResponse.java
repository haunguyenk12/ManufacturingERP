package com.erp.manufacturing.module.shift.dto;

import com.erp.manufacturing.module.shift.domain.Weekday;

import java.util.UUID;

public record WorkCalendarWeeklyShiftResponse(
        UUID workCalendarWeeklyShiftId,
        Weekday weekday,
        UUID shiftId,
        String shiftCode
) {}
