package com.erp.manufacturing.module.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * {@code code} and {@code plantId} are deliberately absent — both are immutable after creation.
 * {@code null} on {@code name}/{@code startTime}/{@code endTime} means "leave unchanged". For
 * {@code breaks}: {@code null} (field omitted) means "leave unchanged", {@code []} means "remove
 * all breaks" — same full-replace-when-present convention as {@code WorkCenterUpdateRequest}.
 */
public record ShiftUpdateRequest(
        @Size(max = 255) String name,
        LocalTime startTime,
        LocalTime endTime,
        List<@Valid ShiftBreakRequest> breaks
) {}
