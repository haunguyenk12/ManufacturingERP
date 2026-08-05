package com.erp.manufacturing.module.shift.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.shift.dto.ShiftCreateRequest;
import com.erp.manufacturing.module.shift.dto.ShiftResponse;
import com.erp.manufacturing.module.shift.dto.ShiftUpdateRequest;
import com.erp.manufacturing.module.shift.service.ShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Shift", description = "Production shift master data")
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping("/api/v1/plants/{plantId}/shifts")
    @Operation(summary = "Create a shift under a plant")
    public ResponseEntity<ApiResponse<ShiftResponse>> create(
            @PathVariable UUID plantId, @Valid @RequestBody ShiftCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(shiftService.create(plantId, request)));
    }

    @GetMapping("/api/v1/plants/{plantId}/shifts")
    @Operation(summary = "List shifts of a plant")
    public ResponseEntity<ApiResponse<PageResult<ShiftResponse>>> list(
            @PathVariable UUID plantId,
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.list(
                plantId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/shifts/{shiftId}")
    @Operation(summary = "Get a shift")
    public ResponseEntity<ApiResponse<ShiftResponse>> get(@PathVariable UUID shiftId) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.get(shiftId)));
    }

    @PatchMapping("/api/v1/shifts/{shiftId}")
    @Operation(summary = "Update name/time/breaks of a shift",
            description = "code and plantId are immutable after creation and are not part of this request body.")
    public ResponseEntity<ApiResponse<ShiftResponse>> update(
            @PathVariable UUID shiftId, @Valid @RequestBody ShiftUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.update(shiftId, request)));
    }

    @PostMapping("/api/v1/shifts/{shiftId}/activate")
    @Operation(summary = "Activate a shift")
    public ResponseEntity<ApiResponse<ShiftResponse>> activate(@PathVariable UUID shiftId) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.activate(shiftId)));
    }

    @PostMapping("/api/v1/shifts/{shiftId}/deactivate")
    @Operation(summary = "Deactivate a shift")
    public ResponseEntity<ApiResponse<ShiftResponse>> deactivate(@PathVariable UUID shiftId) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.deactivate(shiftId)));
    }

    @DeleteMapping("/api/v1/shifts/{shiftId}")
    @Operation(summary = "Deactivate a shift (soft) — this IS the deactivate command",
            description = "Rule C6 forbids hard-deleting master data, so DELETE calls the same "
                    + "service method as POST .../deactivate; nothing is removed.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID shiftId) {
        shiftService.deactivate(shiftId);
        return ResponseEntity.ok(ApiResponse.noContent("Shift deactivated successfully"));
    }
}
