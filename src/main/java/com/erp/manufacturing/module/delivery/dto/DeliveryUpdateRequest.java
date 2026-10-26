package com.erp.manufacturing.module.delivery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** {@code PATCH /v1/deliveries/{id}} (G10b): full replacement of a DRAFT's date, note and lines. */
public record DeliveryUpdateRequest(
        @NotNull Long expectedVersion,
        @NotNull LocalDate documentDate,
        @Size(max = 2000) String note,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull DeliveryLineRequest> lines
) {}
