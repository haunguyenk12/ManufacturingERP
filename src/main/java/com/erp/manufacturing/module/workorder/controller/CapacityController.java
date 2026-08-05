package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityBoardLineResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentRequest;
import com.erp.manufacturing.module.workorder.dto.capacity.ScheduleAdjustmentResponse;
import com.erp.manufacturing.module.workorder.service.ScheduleAdjustmentService;
import com.erp.manufacturing.module.workorder.service.query.CapacityBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Capacity Board + schedule adjustments (C2-8, gap doc §3.6). Static CRP: reports load/capacity
 * computed from schedules {@code WorkOrderService.release()} already wrote — this controller does
 * not itself schedule anything.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Capacity", description = "Capacity Board (static CRP) and manual schedule adjustments")
public class CapacityController {

    private final CapacityBoardService capacityBoardService;
    private final ScheduleAdjustmentService scheduleAdjustmentService;

    @GetMapping("/api/v1/plants/{plantId}/capacity-board")
    @Operation(summary = "Load/capacity per scheduled operation over a date range")
    public ResponseEntity<ApiResponse<PageResult<CapacityBoardLineResponse>>> getBoard(
            @PathVariable UUID plantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID workCenterId,
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "plannedStartAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(capacityBoardService.getBoard(
                plantId, from, to, workCenterId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @PostMapping("/api/v1/work-orders/{workOrderId}/operations/{operationId}/schedule-adjustments")
    @Operation(summary = "Manually adjust a work order operation's planned start/end",
            description = "Never auto-shifts sibling operations; sequence/calendar/capacity conflicts "
                    + "are returned as advisory flags, not rejections.")
    public ResponseEntity<ApiResponse<ScheduleAdjustmentResponse>> adjustSchedule(
            @PathVariable UUID workOrderId, @PathVariable UUID operationId,
            @Valid @RequestBody ScheduleAdjustmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                scheduleAdjustmentService.adjust(workOrderId, operationId, request)));
    }
}
