package com.erp.manufacturing.module.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Optional body of {@code POST /{id}/cancel} (G6a). The legacy call without a body still works; when
 * a body is sent the reason is required and stored on the order.
 */
public record SalesOrderCancelRequest(
        Long expectedVersion,
        @NotBlank @Size(max = 2000) String reason
) {}
