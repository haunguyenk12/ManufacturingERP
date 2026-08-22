package com.erp.manufacturing.module.purchasing.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.purchasing.domain.PurchaseRequisitionStatus;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.service.PurchaseRequisitionService;
import com.erp.manufacturing.common.web.PageableFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Purchase Requisitions", description = "Purchase requisition workflow")
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService purchaseRequisitionService;

    @PostMapping("/v1/supply-suggestions/{suggestionId}/convert-to-purchase-requisition")
    @Operation(summary = "Convert approved BUY supply suggestion to purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> convertSuggestion(
            @PathVariable UUID suggestionId,
            @Valid @RequestBody PurchaseRequisitionFromSuggestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(purchaseRequisitionService.convertFromSuggestion(suggestionId, request)));
    }

    @PostMapping("/v1/purchase-requisitions")
    @Operation(summary = "Create purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> create(
            @Valid @RequestBody PurchaseRequisitionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(purchaseRequisitionService.create(request)));
    }

    @GetMapping("/v1/purchase-requisitions")
    @Operation(summary = "List purchase requisitions")
    public ResponseEntity<ApiResponse<PageResult<PurchaseRequisitionResponse>>> list(
            @RequestParam UUID companyId,
            @RequestParam UUID plantId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) PurchaseRequisitionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseRequisitionService.list(
                companyId, plantId, warehouseId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/v1/purchase-requisitions/{requisitionId}")
    @Operation(summary = "Get purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> get(@PathVariable UUID requisitionId) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseRequisitionService.get(requisitionId)));
    }

    @PostMapping("/v1/purchase-requisitions/{requisitionId}/approve")
    @Operation(summary = "Approve purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> approve(
            @PathVariable UUID requisitionId,
            @Valid @RequestBody(required = false) PurchaseDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseRequisitionService.approve(requisitionId, request)));
    }

    @PostMapping("/v1/purchase-requisitions/{requisitionId}/reject")
    @Operation(summary = "Reject purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> reject(
            @PathVariable UUID requisitionId,
            @Valid @RequestBody(required = false) PurchaseDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseRequisitionService.reject(requisitionId, request)));
    }

    @PostMapping("/v1/purchase-requisitions/{requisitionId}/cancel")
    @Operation(summary = "Cancel purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> cancel(
            @PathVariable UUID requisitionId,
            @Valid @RequestBody(required = false) PurchaseDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(purchaseRequisitionService.cancel(requisitionId, request)));
    }

    @PostMapping("/v1/purchase-requisitions/{requisitionId}/convert-to-purchase-order")
    @Operation(summary = "Convert approved purchase requisition to purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> convertToPurchaseOrder(
            @PathVariable UUID requisitionId,
            @Valid @RequestBody PurchaseRequisitionConvertToOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(purchaseRequisitionService.convertToPurchaseOrder(requisitionId, request)));
    }

}
