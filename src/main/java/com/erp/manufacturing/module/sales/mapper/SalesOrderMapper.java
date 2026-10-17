package com.erp.manufacturing.module.sales.mapper;

import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineMetricsEvidence;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderLinkedDocumentResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderResponse;
import com.erp.manufacturing.module.sales.repository.SalesOrderLineRepository.OrderLineSummaryProjection;
import com.erp.manufacturing.module.sales.repository.SalesOrderPlanningDemandProjection;
import com.erp.manufacturing.module.workorder.service.query.SalesOrderCoverageLookupService.LinkedWorkOrder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class SalesOrderMapper {

    /**
     * Legacy shape: lines without the G6a progress figures (they are {@code null}). The line summary
     * comes from the loaded lines when {@code includeLines}, otherwise it is {@code null}.
     */
    public SalesOrderResponse toResponse(SalesOrder order, boolean includeLines) {
        List<SalesOrderLineResponse> lines = includeLines
                ? sortedLines(order).stream().map(this::toResponse).toList()
                : List.of();
        return build(order, lines, includeLines ? summaryOf(order) : null, List.of());
    }

    /** List row: no lines, the summary from {@code SalesOrderLineRepository.summarizeByOrderIds}. */
    public SalesOrderResponse toListResponse(SalesOrder order, OrderLineSummaryProjection summary) {
        LineSummary lineSummary = summary == null
                ? new LineSummary(null, null, 0)
                : new LineSummary(summary.getEarliestDueDate(), summary.getLatestDueDate(),
                        summary.getLineCount().intValue());
        return build(order, List.of(), lineSummary, List.of());
    }

    /**
     * Detail: every line with its progress figures and the work orders working for the order.
     *
     * @param heldByLine     ACTIVE stock held per line (absent = none)
     * @param coverageByLine open work-order coverage per line (absent = none)
     */
    public SalesOrderResponse toDetailResponse(SalesOrder order,
                                               Map<UUID, BigDecimal> heldByLine,
                                               Map<UUID, BigDecimal> coverageByLine,
                                               List<LinkedWorkOrder> linkedWorkOrders) {
        List<SalesOrderLineResponse> lines = sortedLines(order).stream()
                .map(line -> toResponse(line,
                        heldByLine.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO),
                        coverageByLine.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO)))
                .toList();
        List<SalesOrderLinkedDocumentResponse> documents = linkedWorkOrders.stream()
                .map(linked -> new SalesOrderLinkedDocumentResponse(
                        "WORK_ORDER",
                        linked.workOrderId(),
                        linked.workOrderNo(),
                        linked.status(),
                        linked.salesOrderLineId(),
                        linked.productItemId(),
                        linked.uom(),
                        linked.allocatedQuantity(),
                        linked.fulfilledQuantity()))
                .toList();
        return build(order, lines, summaryOf(order), documents);
    }

    public SalesOrderLineResponse toResponse(SalesOrderLine line) {
        return new SalesOrderLineResponse(
                line.getSalesOrderLineId(),
                line.getLineNo(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                line.getOrderedQuantity(),
                line.getFulfilledQuantity(),
                line.openQuantity(),
                line.getDueDate(),
                null, null, null, null, null, null, null);
    }

    private SalesOrderLineResponse toResponse(SalesOrderLine line, BigDecimal held, BigDecimal coverage) {
        BigDecimal qcAccepted = line.getFulfilledQuantity();
        return new SalesOrderLineResponse(
                line.getSalesOrderLineId(),
                line.getLineNo(),
                line.getItem().getItemId(),
                line.getItem().getCode(),
                line.getItem().getName(),
                line.getItem().getUnit(),
                line.getOrderedQuantity(),
                line.getFulfilledQuantity(),
                line.openQuantity(),
                line.getDueDate(),
                held,
                coverage,
                null,
                qcAccepted,
                null,
                uncovered(line.getOrderedQuantity(), qcAccepted, held, coverage),
                SalesOrderLineMetricsEvidence.current());
    }

    /**
     * {@code max(0, ordered − qcAccepted − held − workOrderCovered)}. The three subtracted figures never
     * overlap: work-order coverage counts only the part QC has not released yet, and holds are capped
     * by this very number when they are made.
     */
    public static BigDecimal uncovered(BigDecimal ordered, BigDecimal qcAccepted,
                                       BigDecimal held, BigDecimal workOrderCovered) {
        BigDecimal rest = ordered.subtract(qcAccepted).subtract(held).subtract(workOrderCovered);
        return rest.signum() > 0 ? rest : BigDecimal.ZERO;
    }

    /** {@code planningDemandId} and the coverage figures come from separate batch lookups. */
    public PlanningDemandLineResponse toResponse(SalesOrderPlanningDemandProjection projection,
                                                 UUID planningDemandId,
                                                 BigDecimal held,
                                                 BigDecimal coverage) {
        BigDecimal rest = projection.getOpenQuantity().subtract(held).subtract(coverage);
        return new PlanningDemandLineResponse(
                projection.getSalesOrderId(),
                projection.getSalesOrderCode(),
                projection.getSalesOrderLineId(),
                planningDemandId,
                projection.getLineNo(),
                projection.getItemId(),
                projection.getItemSku(),
                projection.getItemName(),
                projection.getUom(),
                projection.getOpenQuantity(),
                projection.getDueDate(),
                held,
                coverage,
                rest.signum() > 0 ? rest : BigDecimal.ZERO);
    }

    private SalesOrderResponse build(SalesOrder order,
                                     List<SalesOrderLineResponse> lines,
                                     LineSummary summary,
                                     List<SalesOrderLinkedDocumentResponse> documents) {
        return new SalesOrderResponse(
                order.getSalesOrderId(),
                order.getCompany().getCompanyId(),
                order.getCompany().getCode(),
                order.getPlant().getPlantId(),
                order.getPlant().getCode(),
                order.getOrderNo(),
                order.getCustomerName(),
                order.getOrderDate(),
                order.getStatus().name(),
                order.getNote(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion(),
                lines,
                order.getCustomerId(),
                order.getCustomerCode(),
                order.getCustomerReference(),
                order.getCancelReason(),
                summary != null ? summary.earliest() : null,
                summary != null ? summary.latest() : null,
                summary != null ? summary.count() : null,
                documents);
    }

    private List<SalesOrderLine> sortedLines(SalesOrder order) {
        return order.getLines().stream()
                .sorted(Comparator.comparing(SalesOrderLine::getLineNo))
                .toList();
    }

    private LineSummary summaryOf(SalesOrder order) {
        List<LocalDate> dueDates = order.getLines().stream()
                .map(SalesOrderLine::getDueDate)
                .filter(Objects::nonNull)
                .toList();
        return new LineSummary(
                dueDates.stream().min(Comparator.naturalOrder()).orElse(null),
                dueDates.stream().max(Comparator.naturalOrder()).orElse(null),
                order.getLines().size());
    }

    private record LineSummary(LocalDate earliest, LocalDate latest, Integer count) {}
}
