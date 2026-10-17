package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.dto.InventoryReservationResponse;
import com.erp.manufacturing.module.workorder.service.query.InventoryReservationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory Reservations", description = "Work-order reservations held in a warehouse (G4-4)")
public class InventoryReservationController {

    private final InventoryReservationQueryService reservationQueryService;

    @GetMapping("/v1/inventory/reservations")
    @Operation(summary = "List reservations held in a warehouse (work orders and, since G6a, sales orders)")
    public ResponseEntity<ApiResponse<PageResult<InventoryReservationResponse>>> list(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) MaterialReservationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(reservationQueryService.list(
                warehouseId, itemId, lotId, status,
                PageableFactory.of(page, size, sortBy, sortDir, "reservationId"))));
    }
}
