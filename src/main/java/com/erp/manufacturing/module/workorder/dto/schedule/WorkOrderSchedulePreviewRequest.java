package com.erp.manufacturing.module.workorder.dto.schedule;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code POST /work-orders/{id}/schedule-preview} (G8a). Every field is optional: absent ones fall back to
 * the work order's own start and quantity, so a draft being edited can be previewed before it is saved.
 * {@code expectedVersion}, when sent, is checked like on a command — the preview then answers for the
 * version the client is looking at.
 */
public record WorkOrderSchedulePreviewRequest(
        Long expectedVersion,
        Instant plannedStartAt,
        @Positive @Digits(integer = 13, fraction = 6) BigDecimal plannedQuantity
) {}
