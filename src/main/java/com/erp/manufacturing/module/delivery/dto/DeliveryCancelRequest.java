package com.erp.manufacturing.module.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/deliveries/{id}/cancel} (G10b): a DRAFT only; the reason is 5 to 2000 characters after trim. */
public record DeliveryCancelRequest(@NotNull Long expectedVersion, @NotBlank String reason) {}
