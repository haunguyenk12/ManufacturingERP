package com.erp.manufacturing.module.billing.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/customer-payments/{id}/post} (G10d). */
public record CustomerPaymentPostRequest(@NotNull Long expectedVersion) {}
