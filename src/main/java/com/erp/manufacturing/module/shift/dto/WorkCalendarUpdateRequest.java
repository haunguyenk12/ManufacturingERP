package com.erp.manufacturing.module.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code code} and {@code plantId} are deliberately absent — both are immutable after creation.
 * {@code null} on {@code name}/{@code effectiveFrom}/{@code effectiveTo} means "leave unchanged"
 * (so, unlike create, this endpoint cannot clear an already-set {@code effectiveTo} back to "no
 * expiry" — a minor accepted limitation, not exercised by the phase's required tests). For
 * {@code weeklyShifts}/{@code exceptions}: {@code null} means "leave unchanged", {@code []} means
 * "remove all" — same convention as {@code ShiftUpdateRequest.breaks}.
 */
public record WorkCalendarUpdateRequest(
        @Size(max = 255) String name,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<@Valid WorkCalendarWeeklyShiftRequest> weeklyShifts,
        List<@Valid WorkCalendarExceptionRequest> exceptions
) {}
