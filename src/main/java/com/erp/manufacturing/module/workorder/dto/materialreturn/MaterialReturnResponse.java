package com.erp.manufacturing.module.workorder.dto.materialreturn;

import com.erp.manufacturing.module.workorder.domain.MaterialReturnStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A posted material return (G8d). {@code workOrderVersion} is the work order's current version when the response was
 * built. No value fields: issue and return values are not tracked yet (D10).
 */
public record MaterialReturnResponse(
        UUID materialReturnId,
        String code,
        UUID workOrderId,
        String workOrderCode,
        MaterialReturnStatus status,
        String reason,
        Instant postedAt,
        UUID postedByUserId,
        String postedByUsername,
        String traceId,
        Long workOrderVersion,
        List<MaterialReturnLineResponse> lines
) {}
