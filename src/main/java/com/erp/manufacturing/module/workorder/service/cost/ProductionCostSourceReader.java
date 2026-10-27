package com.erp.manufacturing.module.workorder.service.cost;

import com.erp.manufacturing.module.delivery.domain.DeliveryLine;
import com.erp.manufacturing.module.delivery.service.DeliveryLookupService;
import com.erp.manufacturing.module.inventory.service.StockPoolLookupService;
import com.erp.manufacturing.module.inventory.service.StockPoolLookupService.PoolMovement;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueLine;
import com.erp.manufacturing.module.workorder.domain.MaterialIssueStatus;
import com.erp.manufacturing.module.workorder.domain.MaterialReturnLine;
import com.erp.manufacturing.module.workorder.domain.ProductionExecution;
import com.erp.manufacturing.module.workorder.domain.ProductionQualityDecision;
import com.erp.manufacturing.module.workorder.domain.ProductionReceipt;
import com.erp.manufacturing.module.workorder.domain.ProductionReceiptLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.dto.cost.CostBaselineDtos.Baseline;
import com.erp.manufacturing.module.workorder.repository.ComponentQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.KeyedQuantityProjection;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueLineRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialIssueRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import com.erp.manufacturing.module.workorder.repository.MaterialReturnLineRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionConsumptionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionExecutionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionQualityDecisionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptRepository;
import com.erp.manufacturing.module.workorder.service.execution.OperationLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * G11b: reads every source of a work order's cost in one transaction — a fixed number of queries whatever its size
 * (C14) — and fingerprints them as the opaque {@code sourceVersion} (FE Q31, the {@code billingSnapshotVersion} pattern
 * of G10c, B247). The fingerprint covers the work order version, the baseline, issues, consumption, returns,
 * executions, operation pools, receipts, QC decisions, pool deliveries and pool movements; it deliberately leaves out
 * invoices and payments (FE R18, Q25) and contains no amount that can be decoded.
 */
@Component
@RequiredArgsConstructor
public class ProductionCostSourceReader {

    static final String WORK_ORDER_REFERENCE = "WORK_ORDER";
    static final String QC_DECISION_REFERENCE = "PRODUCTION_QC_DECISION";
    static final String DELIVERY_REFERENCE = "DELIVERY";

    private final ProductionCostBaselineService baselineService;
    private final MaterialIssueLineRepository issueLineRepository;
    private final ProductionExecutionConsumptionRepository consumptionRepository;
    private final MaterialReturnLineRepository returnLineRepository;
    private final ProductionExecutionRepository executionRepository;
    private final OperationLedger operationLedger;
    private final ProductionReceiptRepository receiptRepository;
    private final ProductionQualityDecisionRepository decisionRepository;
    private final MaterialIssueRepository issueRepository;
    private final MaterialReservationRepository reservationRepository;
    private final DeliveryLookupService deliveryLookupService;
    private final StockPoolLookupService stockPoolLookupService;

    public record Sources(CostInputs inputs, String sourceVersion) {
    }

    @Transactional(readOnly = true)
    public Sources read(WorkOrder workOrder) {
        UUID workOrderId = workOrder.getWorkOrderId();
        Baseline baseline = baselineService.load(workOrderId).orElse(null);

        List<MaterialIssueLine> issueLines = issueLineRepository.findPostedByWorkOrder(workOrderId);
        List<UUID> issueLineIds = issueLines.stream().map(MaterialIssueLine::getIssueLineId).toList();
        Map<UUID, BigDecimal> consumed = issueLineIds.isEmpty() ? Map.of()
                : consumptionRepository.sumByIssueLineIds(issueLineIds).stream()
                .collect(Collectors.toMap(KeyedQuantityProjection::getRefId, KeyedQuantityProjection::getQuantity));
        Map<UUID, List<CostInputs.Return>> returns = issueLineIds.isEmpty() ? Map.of()
                : returnLineRepository.findPostedBySourceIssueLineIds(issueLineIds).stream()
                .collect(Collectors.groupingBy(line -> line.getSourceIssueLine().getIssueLineId(),
                        Collectors.mapping(ProductionCostSourceReader::toReturn, Collectors.toList())));
        List<CostInputs.IssueLine> issues = issueLines.stream().map(line -> new CostInputs.IssueLine(
                line.getIssue().getIssueId(), line.getIssue().getCode(), line.getIssueLineId(),
                line.getStockMovement() == null ? null : line.getStockMovement().getMovementId(),
                line.getComponentLine().getComponentLineId(), line.getItem().getItemId(), line.getItem().getCode(),
                line.getWarehouse().getWarehouseId(), line.getLot() == null ? null : line.getLot().getLotId(),
                line.getItem().getUnit(), line.getQuantity(),
                consumed.getOrDefault(line.getIssueLineId(), BigDecimal.ZERO),
                returns.getOrDefault(line.getIssueLineId(), List.of()))).toList();

        List<CostInputs.Requirement> requirements = workOrder.getComponentLines().stream()
                .sorted(Comparator.comparing(WorkOrderComponentLine::getLineNo))
                .map(line -> new CostInputs.Requirement(line.getComponentLineId(), line.getRequiredQuantity()))
                .toList();

        List<CostInputs.Operation> operations = operationLedger.load(workOrderId).stream()
                .map(state -> new CostInputs.Operation(state.operation().getWorkOrderOperationId(),
                        state.operation().getSequence(), state.operation().getName(), state.operation().isCompleted(),
                        state.last(), state.input(), state.freshProcessed(), state.pendingRework(),
                        state.transferable(), state.good()))
                .toList();
        List<CostInputs.Execution> executions = executionRepository.findByWorkOrderWorkOrderIdOrderByCreatedAtAsc(workOrderId)
                .stream().map(ProductionCostSourceReader::toExecution).toList();

        Map<UUID, List<ProductionQualityDecision>> decisions = decisionRepository.findByWorkOrder(workOrderId).stream()
                .collect(Collectors.groupingBy(decision -> decision.getReceipt().getReceiptId()));
        List<CostInputs.Receipt> receipts = receiptRepository.findApprovedWithLinesByWorkOrder(workOrderId).stream()
                .sorted(Comparator.comparing(ProductionReceipt::getApprovedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(receipt -> receipt.getReceiptId().toString()))
                .map(receipt -> toReceipt(receipt, decisions.getOrDefault(receipt.getReceiptId(), List.of())))
                .toList();

        BigDecimal open = receiptRepository.sumOpenQuantityByWorkOrderId(workOrderId);
        boolean pendingIssues = issueRepository.existsByWorkOrderWorkOrderIdAndStatus(workOrderId,
                MaterialIssueStatus.PENDING_APPROVAL);
        BigDecimal reserved = reservationRepository.sumActiveRemainingByWorkOrderId(workOrderId).stream()
                .map(ComponentQuantityProjection::getQuantity).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        UUID itemId = workOrder.getProductItem().getItemId();
        UUID warehouseId = workOrder.getOutputWarehouse().getWarehouseId();
        List<CostInputs.DeliveryLine> deliveries = deliveryLookupService.findPostedPoolLines(itemId, warehouseId).stream()
                .map(ProductionCostSourceReader::toDelivery).toList();
        List<PoolMovement> movements = baseline == null ? List.of()
                : stockPoolLookupService.movementsSince(itemId, warehouseId, baseline.capturedAt());
        Set<String> decisionIds = decisions.values().stream().flatMap(List::stream)
                .map(decision -> decision.getDecisionId().toString()).collect(Collectors.toSet());
        Set<String> deliveryIds = deliveries.stream().map(line -> line.deliveryId().toString()).collect(Collectors.toSet());
        int foreign = (int) movements.stream()
                .filter(movement -> !ownMovement(movement, workOrderId.toString(), decisionIds, deliveryIds))
                .count();

        CostInputs inputs = new CostInputs(workOrderId, workOrder.getStatus(), workOrder.isOperationTracking(),
                workOrder.getPlannedQuantity(), workOrder.getActualGoodQuantity(), workOrder.getActualScrapQuantity(),
                workOrder.getActualReworkQuantity(), baseline, requirements, issues, operations, executions, receipts,
                open != null && open.signum() > 0, pendingIssues, reserved, deliveries, foreign);
        return new Sources(inputs, fingerprint(workOrder, inputs, movements));
    }

    /** D3: what may move the output pool after release — this order's receipts, its QC splits and deliveries. */
    static boolean ownMovement(PoolMovement movement, String workOrderId, Set<String> decisionIds, Set<String> deliveryIds) {
        String type = movement.referenceType();
        String id = movement.referenceId();
        return (WORK_ORDER_REFERENCE.equals(type) && workOrderId.equals(id))
                || (QC_DECISION_REFERENCE.equals(type) && decisionIds.contains(id))
                || (DELIVERY_REFERENCE.equals(type) && deliveryIds.contains(id));
    }

    private static CostInputs.Return toReturn(MaterialReturnLine line) {
        return new CostInputs.Return(line.getMaterialReturn().getMaterialReturnId(), line.getMaterialReturn().getCode(),
                line.getMaterialReturnLineId(),
                line.getStockMovement() == null ? null : line.getStockMovement().getMovementId(),
                line.getQuantity(), line.getMaterialReturn().getPostedAt());
    }

    private static CostInputs.Execution toExecution(ProductionExecution execution) {
        return new CostInputs.Execution(execution.getProductionExecutionId(), execution.getCode(),
                execution.getOperation() == null ? null : execution.getOperation().getWorkOrderOperationId(),
                execution.getSourceExecutionId(), execution.getActualStartedAt(), execution.getActualEndedAt());
    }

    private static CostInputs.Receipt toReceipt(ProductionReceipt receipt, List<ProductionQualityDecision> decisions) {
        ProductionReceiptLine line = receipt.getLines().isEmpty() ? null : receipt.getLines().get(0);
        BigDecimal quantity = receipt.getLines().stream().map(ProductionReceiptLine::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CostInputs.Decision> ordered = decisions.stream()
                .sorted(Comparator.comparing(ProductionQualityDecision::getDecidedAt)
                        .thenComparing(decision -> decision.getDecisionId().toString()))
                .map(decision -> new CostInputs.Decision(decision.getDecisionId(), decision.getDecidedAt(),
                        decision.getAcceptedQuantity(), decision.getRejectedQuantity(),
                        decision.getAcceptedLot() == null ? null : decision.getAcceptedLot().getLotId(),
                        decision.getRejectedLot() == null ? null : decision.getRejectedLot().getLotId()))
                .toList();
        return new CostInputs.Receipt(receipt.getReceiptId(), receipt.getCode(),
                line == null ? null : line.getWarehouse().getWarehouseId(), receipt.getApprovedAt(), quantity,
                line == null || line.getLot() == null ? null : line.getLot().getLotId(), ordered);
    }

    private static CostInputs.DeliveryLine toDelivery(DeliveryLine line) {
        UUID movement = line.getFreeMovementId() != null ? line.getFreeMovementId() : line.getReservedMovementId();
        return new CostInputs.DeliveryLine(line.getDelivery().getDeliveryId(), line.getDelivery().getCode(),
                line.getDeliveryLineId(), line.getLotId(), line.getQuantity(), movement);
    }

    /** {@code cs1-} + 32 hex of a SHA-256 over every source revision; stable across reads of the same sources. */
    static String fingerprint(WorkOrder workOrder, CostInputs in, List<PoolMovement> movements) {
        List<String> parts = new ArrayList<>();
        parts.add("wo:" + workOrder.getWorkOrderId() + ":" + workOrder.getVersion() + ":" + workOrder.getStatus());
        parts.add("baseline:" + (in.baseline() == null ? "-" : in.baseline().baselineId()));
        in.issueLines().forEach(line -> {
            parts.add("issue:" + line.issueLineId() + ":" + plain(line.quantity()) + ":" + plain(line.consumed()));
            line.returns().forEach(ret -> parts.add("return:" + ret.returnLineId() + ":" + plain(ret.quantity())));
        });
        in.operations().forEach(op -> parts.add("op:" + op.operationId() + ":" + op.completed() + ":" + plain(op.input())
                + ":" + plain(op.freshProcessed()) + ":" + plain(op.pendingRework()) + ":" + plain(op.transferable())));
        in.executions().forEach(execution -> parts.add("exec:" + execution.executionId() + ":" + execution.startedAt()
                + ":" + execution.endedAt()));
        in.receipts().forEach(receipt -> {
            parts.add("receipt:" + receipt.receiptId() + ":" + plain(receipt.quantity()));
            receipt.decisions().forEach(decision -> parts.add("qc:" + decision.decisionId()));
        });
        parts.add("open:" + in.openReceipts() + ":" + in.pendingIssues() + ":" + plain(in.activeReservedQuantity()));
        in.deliveries().forEach(line -> parts.add("delivery:" + line.lineId() + ":" + plain(line.quantity())));
        movements.forEach(movement -> parts.add("move:" + movement.movementId()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return "cs1-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
