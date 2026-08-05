package com.erp.manufacturing.module.shift.dto;

import java.time.LocalDate;
import java.util.UUID;

public record WorkCalendarExceptionResponse(
        UUID workCalendarExceptionId,
        LocalDate exceptionDate,
        String reason
) {}
