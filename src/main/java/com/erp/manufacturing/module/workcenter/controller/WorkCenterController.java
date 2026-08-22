package com.erp.manufacturing.module.workcenter.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterCreateRequest;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterResponse;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterUpdateRequest;
import com.erp.manufacturing.module.workcenter.service.WorkCenterService;
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
@Tag(name = "Work Center", description = "Production work center master data")
public class WorkCenterController {

    private final WorkCenterService workCenterService;

    @PostMapping("/v1/plants/{plantId}/work-centers")
    @Operation(summary = "Create a work center under a plant")
    public ResponseEntity<ApiResponse<WorkCenterResponse>> create(
            @PathVariable UUID plantId, @Valid @RequestBody WorkCenterCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(workCenterService.create(plantId, request)));
    }

    @GetMapping("/v1/plants/{plantId}/work-centers")
    @Operation(summary = "List work centers of a plant")
    public ResponseEntity<ApiResponse<PageResult<WorkCenterResponse>>> list(
            @PathVariable UUID plantId,
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(workCenterService.list(
                plantId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/work-centers/{workCenterId}")
    @Operation(summary = "Get a work center")
    public ResponseEntity<ApiResponse<WorkCenterResponse>> get(@PathVariable UUID workCenterId) {
        return ResponseEntity.ok(ApiResponse.ok(workCenterService.get(workCenterId)));
    }

    @PatchMapping("/v1/work-centers/{workCenterId}")
    @Operation(summary = "Update name/description/capacity of a work center",
            description = "code and plantId are immutable after creation and are not part of this request body.")
    public ResponseEntity<ApiResponse<WorkCenterResponse>> update(
            @PathVariable UUID workCenterId, @Valid @RequestBody WorkCenterUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workCenterService.update(workCenterId, request)));
    }

    @PostMapping("/v1/work-centers/{workCenterId}/activate")
    @Operation(summary = "Activate a work center")
    public ResponseEntity<ApiResponse<WorkCenterResponse>> activate(@PathVariable UUID workCenterId) {
        return ResponseEntity.ok(ApiResponse.ok(workCenterService.activate(workCenterId)));
    }

    @PostMapping("/v1/work-centers/{workCenterId}/deactivate")
    @Operation(summary = "Deactivate a work center")
    public ResponseEntity<ApiResponse<WorkCenterResponse>> deactivate(@PathVariable UUID workCenterId) {
        return ResponseEntity.ok(ApiResponse.ok(workCenterService.deactivate(workCenterId)));
    }

    @DeleteMapping("/v1/work-centers/{workCenterId}")
    @Operation(summary = "Deactivate a work center (soft) — this IS the deactivate command",
            description = "Rule C6 forbids hard-deleting master data, so DELETE calls the same "
                    + "service method as POST .../deactivate; nothing is removed.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID workCenterId) {
        workCenterService.deactivate(workCenterId);
        return ResponseEntity.ok(ApiResponse.noContent("Work center deactivated successfully"));
    }
}
