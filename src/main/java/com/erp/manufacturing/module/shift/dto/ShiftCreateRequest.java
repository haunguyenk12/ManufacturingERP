package com.erp.manufacturing.module.shift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * {@code breaks} may be omitted or {@code []} — both mean "no breaks". {@code endTime} before
 * {@code startTime} is a valid overnight shift, not a validation error (decision §1.1).
 */
public record ShiftCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        List<@Valid ShiftBreakRequest> breaks
) {}
