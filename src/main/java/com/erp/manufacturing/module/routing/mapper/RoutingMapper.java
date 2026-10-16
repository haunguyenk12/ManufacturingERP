package com.erp.manufacturing.module.routing.mapper;

import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.dto.RoutingOperationResponse;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class RoutingMapper {

    public RoutingResponse toResponse(RoutingHeader routing) {
        List<RoutingOperation> sorted = routing.getOperations() == null
                ? List.of()
                : routing.getOperations().stream()
                .sorted(Comparator.comparing(RoutingOperation::getSequence))
                .toList();
        List<RoutingOperationResponse> operations = sorted.stream().map(this::toResponse).toList();

        // All operations share one plant (B_wc2); legacy operations without a work center have none.
        UUID plantId = sorted.stream()
                .map(RoutingOperation::getWorkCenter)
                .filter(Objects::nonNull)
                .map(workCenter -> workCenter.getPlant().getPlantId())
                .findFirst()
                .orElse(null);

        return new RoutingResponse(
                routing.getRoutingId(),
                routing.getCompany().getCompanyId(),
                routing.getItem().getItemId(),
                routing.getItem().getCode(),
                routing.getItem().getName(),
                routing.getCode(),
                routing.getRoutingVersion(),
                routing.getStatus().name(),
                routing.getNote(),
                routing.getCreatedAt(),
                routing.getUpdatedAt(),
                operations,
                routing.getName(),
                routing.getVersionNumber(),
                routing.getEffectiveFrom(),
                plantId,
                sorted.stream().map(RoutingOperation::getSetupMinutes).reduce(BigDecimal.ZERO, BigDecimal::add),
                sorted.stream().map(RoutingOperation::getRunMinutesPerUnit).reduce(BigDecimal.ZERO, BigDecimal::add),
                routing.getVersion());
    }

    public RoutingOperationResponse toResponse(RoutingOperation operation) {
        // workCenter is null for rows created before C2-6 (not backfilled — see RoutingOperation
        // javadoc); both id and derived code fall back to null rather than throwing.
        var workCenter = operation.getWorkCenter();
        List<UUID> predecessorIds = operation.getPredecessors() == null
                ? List.of()
                : operation.getPredecessors().stream()
                .sorted(Comparator.comparing(RoutingOperation::getSequence))
                .map(RoutingOperation::getRoutingOperationId)
                .toList();
        return new RoutingOperationResponse(
                operation.getRoutingOperationId(),
                operation.getSequence(),
                operation.getName(),
                workCenter == null ? null : workCenter.getWorkCenterId(),
                workCenter == null ? null : workCenter.getCode(),
                operation.getSetupMinutes(),
                operation.getRunMinutesPerUnit(),
                workCenter == null ? null : workCenter.getName(),
                predecessorIds);
    }
}
