package com.erp.manufacturing.module.shift.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record WorkCalendarExceptionRequest(
        @NotNull LocalDate exceptionDate,
        @Size(max = 255) String reason
) {}
