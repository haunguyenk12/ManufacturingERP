package com.erp.manufacturing.module.sales.dto;

import java.util.List;

/**
 * Result of a hold/release (G6a): the reservation rows touched and the order as it now stands — new
 * {@code version} for the next command and recalculated line figures, so the client does not have to
 * refetch to know what is still uncovered.
 */
public record SalesOrderReservationResult(
        List<SalesOrderReservationResponse> reservations,
        SalesOrderResponse salesOrder
) {}
