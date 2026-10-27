package com.erp.manufacturing.module.workorder.controller;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.BaselineView;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Adjustment;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Checklist;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.CostView;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.DeliveryCost;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.FinalizeRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.ListRow;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Readiness;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunCommandResult;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.RunSummary;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostDtos.Section;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateCommandResult;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateRequest;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateResponse;
import com.erp.manufacturing.module.workorder.dto.cost.ProductionCostRateDtos.RateRowResponse;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostBaselineService;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostCloseService;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostRateService;
import com.erp.manufacturing.module.workorder.service.cost.ProductionCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * G11: production cost rates, cost baselines, cost calculation and finalize-and-close (FE C1–C13). The plant-scoped
 * endpoints cross-check {@code X-Plant-Id}; those identified by a work order, run or delivery do not — the id is the
 * scope ({@code error-handling.md §5.6.1}). Commands require {@code Idempotency-Key} and return identity and versions
 * only; the client reads back what it is allowed to see.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Production cost", description = "Cost rates, baselines, production cost and finalize-and-close (G11)")
public class ProductionCostController {

    private final ProductionCostRateService rateService;
    private final ProductionCostBaselineService baselineService;
    private final ProductionCostService costService;
    private final ProductionCostCloseService closeService;
    private final PlantContextResolver plantContextResolver;

    @GetMapping("/v1/plants/{plantId}/production-cost-rates")
    @Operation(summary = "C1 · Hourly cost rates of the plant's work centers",
            description = "Minimal work center + current rate (null when never priced). Needs PERM_COST_RATE_MANAGE, "
                    + "PERM_WORK_ORDER_VARIANCE_READ or PERM_PRODUCTION_COST_CALCULATE on the plant.")
    public ResponseEntity<ApiResponse<PageResult<RateRowResponse>>> listRates(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @PathVariable UUID plantId,
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(rateService.list(plantId, status, search,
                PageableFactory.of(page, size, "code", "asc", "workCenterId"))));
    }

    @PutMapping("/v1/plants/{plantId}/production-cost-rates/{workCenterId}")
    @Operation(summary = "C2 · Save the hourly labor/overhead rate of a work center",
            description = "Body {expectedVersion, laborRate, overheadRate, reason (5-2000), currency=VND}. Writes version "
                    + "n+1; never changes a released work order's baseline. Idempotency-Key required. Needs "
                    + "PERM_COST_RATE_MANAGE.")
    public ResponseEntity<ApiResponse<RateCommandResult>> saveRate(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID plantId,
            @PathVariable UUID workCenterId,
            @Valid @RequestBody RateRequest request) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(rateService.save(plantId, workCenterId, request, idempotencyKey)));
    }

    @GetMapping("/v1/plants/{plantId}/production-cost-rates/{workCenterId}/history")
    @Operation(summary = "C13 · Rate history of a work center", description = "Newest version first.")
    public ResponseEntity<ApiResponse<PageResult<RateResponse>>> rateHistory(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @PathVariable UUID plantId,
            @PathVariable UUID workCenterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        return ResponseEntity.ok(ApiResponse.ok(rateService.history(plantId, workCenterId,
                PageableFactory.of(page, size, "rateVersion", "desc"))));
    }

    @GetMapping("/v1/plants/{plantId}/production-costs")
    @Operation(summary = "C3 · Production cost / close list of the plant",
            description = "mode=costs (default; RELEASED..CLOSED, needs cost view) or close (COMPLETED/CLOSED, also readable "
                    + "with PERM_WORK_ORDER_CLOSE / _READ / PERM_WIP_READ — without cost view money is absent). "
                    + "fromDate/toDate are plant-local days on createdAt, inclusive.")
    public ResponseEntity<ApiResponse<PageResult<ListRow>>> listCosts(
            @RequestHeader(value = PlantContextResolver.HEADER, required = false) String plantHeader,
            @PathVariable UUID plantId,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String readiness,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        plantContextResolver.ensureMatches(plantHeader, plantId);
        String shownMode = mode == null ? "costs" : mode.toLowerCase(Locale.ROOT);
        if (!shownMode.equals("costs") && !shownMode.equals("close")) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of("mode", "must be costs or close"));
        }
        return ResponseEntity.ok(ApiResponse.ok(costService.list(plantId, shownMode, search,
                parse(Readiness.class, readiness, "readiness"), fromDate, toDate,
                PageableFactory.of(page, size, "createdAt", "desc", "workOrderId"))));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/production-costs")
    @Operation(summary = "C4 · Production cost of a work order",
            description = "section = overview (default) | materials | operations | variance | allocation; collections are "
                    + "paged. Live values: planned from the release baseline, actual from consumption, returns and "
                    + "execution time. Needs PERM_WORK_ORDER_VARIANCE_READ or PERM_PRODUCTION_COST_CALCULATE.")
    public ResponseEntity<ApiResponse<CostView>> getCost(
            @PathVariable UUID workOrderId,
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(costService.get(workOrderId, parse(Section.class, section, "section"),
                page, size)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/cost-baseline")
    @Operation(summary = "C5 · Cost baseline captured at release", description = "baseline null for work orders released before G11.")
    public ResponseEntity<ApiResponse<BaselineView>> getBaseline(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(baselineService.get(workOrderId)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/cost-checklist")
    @Operation(summary = "C6 · The 11 close checks of a work order",
            description = "Readable by cost view, PERM_WORK_ORDER_CLOSE, PERM_WORK_ORDER_READ or PERM_WIP_READ; "
                    + "sourceVersion and cost are absent without cost view.")
    public ResponseEntity<ApiResponse<Checklist>> getChecklist(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(ApiResponse.ok(costService.checklist(workOrderId)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/cost-runs")
    @Operation(summary = "C7 · Calculate and save a production cost preview",
            description = "Body {expectedVersion, expectedSourceVersion}. No stock, value or work order change. "
                    + "Idempotency-Key required. Needs PERM_PRODUCTION_COST_CALCULATE.")
    public ResponseEntity<ApiResponse<RunCommandResult>> createRun(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RunRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(costService.createRun(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/cost-runs")
    @Operation(summary = "C8 · Cost runs of a work order", description = "Newest first.")
    public ResponseEntity<ApiResponse<PageResult<RunSummary>>> listRuns(
            @PathVariable UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(costService.runs(workOrderId, PageableFactory.unsorted(page, size))));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/cost-runs/{runId}")
    @Operation(summary = "C9 · One cost run", description = "The run's own immutable snapshot, by section like C4.")
    public ResponseEntity<ApiResponse<CostView>> getRun(
            @PathVariable UUID workOrderId,
            @PathVariable UUID runId,
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(costService.run(workOrderId, runId,
                parse(Section.class, section, "section"), page, size)));
    }

    @PostMapping("/v1/work-orders/{workOrderId}/finalize-cost-and-close")
    @Operation(summary = "C10 · Finalize the production cost and close the work order (atomic)",
            description = "Body {expectedVersion, expectedSourceVersion, runId, expectedRunVersion, reason (5-2000)}. "
                    + "Re-calculates on the server, checks the 11 conditions, writes the final snapshot and value-only "
                    + "entries and CLOSED in one transaction. Idempotency-Key required. Needs PERM_WORK_ORDER_CLOSE and "
                    + "cost view.")
    public ResponseEntity<ApiResponse<RunCommandResult>> finalizeAndClose(
            @PathVariable UUID workOrderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody FinalizeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(closeService.finalizeAndClose(workOrderId, request, idempotencyKey)));
    }

    @GetMapping("/v1/deliveries/{deliveryId}/production-cost")
    @Operation(summary = "C11 · Effective production cost of a delivery's lines",
            description = "Needs the delivery read authority and cost view on its plant.")
    public ResponseEntity<ApiResponse<DeliveryCost>> deliveryCost(@PathVariable UUID deliveryId) {
        return ResponseEntity.ok(ApiResponse.ok(closeService.deliveryCost(deliveryId)));
    }

    @GetMapping("/v1/work-orders/{workOrderId}/cost-runs/{runId}/adjustments")
    @Operation(summary = "C12 · Value-only entries written by a finalized run", description = "Quantity is always 0.")
    public ResponseEntity<ApiResponse<PageResult<Adjustment>>> adjustments(
            @PathVariable UUID workOrderId,
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(closeService.adjustments(workOrderId, runId,
                PageableFactory.unsorted(page, size))));
    }

    /** Case-insensitive enum query value; an unknown one is a 400 on its field. */
    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, Map.of(field, "unknown value: " + value));
        }
    }
}
