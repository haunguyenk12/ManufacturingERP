package com.erp.manufacturing.module.delivery.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/deliveries/{id}/post} (G10b). */
public record DeliveryPostRequest(@NotNull Long expectedVersion) {}
