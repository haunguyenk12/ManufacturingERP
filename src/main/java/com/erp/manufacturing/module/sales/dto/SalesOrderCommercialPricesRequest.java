package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /sales-orders/v1/{id}/commercial-prices} (G10a): fill missing prices of a confirmed order. */
public record SalesOrderCommercialPricesRequest(
        @NotNull Long expectedVersion,
        @NotNull @Size(min = 1, max = 500) List<@Valid @NotNull SalesOrderLinePriceRequest> prices
) {}
