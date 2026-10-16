package com.erp.manufacturing.module.bom.dto;

/**
 * Body of activate/deactivate. Optional on activate, where the endpoint predates it; when sent, a
 * stale value is a 409 {@code CONCURRENT_MODIFICATION} and nothing changes.
 */
public record BomVersionRequest(Long expectedVersion) {}
