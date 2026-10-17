package com.erp.manufacturing.module.sales.dto;

/**
 * Optional body of {@code POST /{id}/confirm} (G6a). No body, or a {@code null} version, keeps the
 * legacy behaviour (no version check); a version that does not match is a 409.
 */
public record SalesOrderVersionRequest(Long expectedVersion) {}
