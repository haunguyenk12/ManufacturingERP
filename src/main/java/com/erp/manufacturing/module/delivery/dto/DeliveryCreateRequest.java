package com.erp.manufacturing.module.delivery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code POST /v1/deliveries} (G10b): a DRAFT for one sales order; the customer and plant come from the order. */
public record DeliveryCreateRequest(
        @NotNull UUID salesOrderId,
        @NotNull Long expectedOrderVersion,
        @NotNull LocalDate documentDate,
        @Size(max = 2000) String note,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull DeliveryLineRequest> lines
) {}
