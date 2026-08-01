package com.erp.manufacturing.module.routing.mapper;

import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.dto.RoutingOperationResponse;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class RoutingMapper {

    public RoutingResponse toResponse(RoutingHeader routing) {
        List<RoutingOperationResponse> operations = routing.getOperations() == null
                ? List.of()
                : routing.getOperations().stream()
                .sorted(Comparator.comparing(RoutingOperation::getSequence))
                .map(this::toResponse)
                .toList();

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
                operations);
    }

    public RoutingOperationResponse toResponse(RoutingOperation operation) {
        return new RoutingOperationResponse(
                operation.getRoutingOperationId(),
                operation.getSequence(),
                operation.getName(),
                operation.getWorkCenterCode(),
                operation.getSetupMinutes(),
                operation.getRunMinutesPerUnit());
    }
}
