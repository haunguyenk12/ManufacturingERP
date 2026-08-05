package com.erp.manufacturing.module.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code effectiveTo} of {@code null} means no expiry. {@code weeklyShifts}/{@code exceptions} may
 * be omitted or {@code []} — both mean "none yet".
 */
public record WorkCalendarCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<@Valid WorkCalendarWeeklyShiftRequest> weeklyShifts,
        List<@Valid WorkCalendarExceptionRequest> exceptions
) {}
