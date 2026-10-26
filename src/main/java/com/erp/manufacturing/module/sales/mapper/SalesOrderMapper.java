package com.erp.manufacturing.module.sales.mapper;

import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.dto.PlanningDemandLineResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderCommercialResponse;
import com.erp.manufacturing.module.sales.dto.SalesOrderLineCommercialResponse;
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
        return build(order, lines, includeLines ? summaryOf(order) : null, List.of(), false);
    }

    /** List row: no lines, the summary from {@code SalesOrderLineRepository.summarizeByOrderIds}. */
    public SalesOrderResponse toListResponse(SalesOrder order, OrderLineSummaryProjection summary) {
        LineSummary lineSummary = summary == null
                ? new LineSummary(null, null, 0)
                : new LineSummary(summary.getEarliestDueDate(), summary.getLatestDueDate(),
                        summary.getLineCount().intValue());
        return build(order, List.of(), lineSummary, List.of(), false);
    }

    /**
     * Detail: every line with its progress figures and the work orders working for the order.
     *
     * @param heldByLine     ACTIVE stock held per line (absent = none)
     * @param coverageByLine open work-order coverage per line (absent = none)
     * @param showPrices     G10a (D6): whether the caller may see the order's money
     * @param deliveredByLine G10b: POSTED delivery quantity per line (absent = none)
     */
    public SalesOrderResponse toDetailResponse(SalesOrder order,
                                               Map<UUID, BigDecimal> heldByLine,
                                               Map<UUID, BigDecimal> coverageByLine,
                                               List<LinkedWorkOrder> linkedWorkOrders,
                                               boolean showPrices,
                                               Map<UUID, BigDecimal> deliveredByLine) {
        List<SalesOrderLineResponse> lines = sortedLines(order).stream()
                .map(line -> toResponse(line,
                        heldByLine.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO),
                        coverageByLine.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO),
                        showPrices,
                        order.isDeliveryBaselineKnown()
                                ? deliveredByLine.getOrDefault(line.getSalesOrderLineId(), BigDecimal.ZERO)
                                : null))
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
        return build(order, lines, summaryOf(order), documents, showPrices);
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

    /** @param delivered {@code null} when the order's delivery baseline is unknown (D3) — never a guessed 0 */
    private SalesOrderLineResponse toResponse(SalesOrderLine line, BigDecimal held, BigDecimal coverage,
                                              boolean showPrices, BigDecimal delivered) {
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
                delivered,
                uncovered(line.getOrderedQuantity(), qcAccepted, delivered, held, coverage),
                SalesOrderLineMetricsEvidence.of(delivered != null),
                showPrices ? commercialOf(line) : null);
    }

    private static SalesOrderLineCommercialResponse commercialOf(SalesOrderLine line) {
        return new SalesOrderLineCommercialResponse(
                line.getUnitPrice(), SalesOrderCommercialResponse.VND, line.getUnitPrice() != null);
    }

    /**
     * {@code max(0, ordered − max(qcAccepted, delivered) − held − workOrderCovered)} (G10b, D8). The subtracted
     * figures never overlap: work-order coverage counts only the part QC has not released yet, and holds are capped
     * by this very number when they are made. {@code delivered} enters as {@code max} with {@code qcAccepted}, not as
     * a fourth subtraction: goods QC released and then shipped are counted once (a straight subtraction makes
     * "ordered 10, QC 6, shipped those 6" read as nothing left to make), while a shipment from a hold or from free
     * stock no longer leaves its quantity planned again. {@code delivered} {@code null} (unknown baseline) counts 0.
     */
    public static BigDecimal uncovered(BigDecimal ordered, BigDecimal qcAccepted, BigDecimal delivered,
                                       BigDecimal held, BigDecimal workOrderCovered) {
        BigDecimal satisfied = delivered == null ? qcAccepted : qcAccepted.max(delivered);
        BigDecimal rest = ordered.subtract(satisfied).subtract(held).subtract(workOrderCovered);
        return rest.signum() > 0 ? rest : BigDecimal.ZERO;
    }

    /**
     * {@code planningDemandId}, the coverage figures and (G10b) the delivered quantity come from separate batch
     * lookups. {@code open} is {@code ordered − fulfilled}, so {@code ordered − max(fulfilled, delivered)} is
     * {@code open − max(0, delivered − fulfilled)} — the same D8 formula as {@link #uncovered}.
     */
    public PlanningDemandLineResponse toResponse(SalesOrderPlanningDemandProjection projection,
                                                 UUID planningDemandId,
                                                 BigDecimal held,
                                                 BigDecimal coverage,
                                                 BigDecimal deliveredBeyondQc) {
        BigDecimal rest = projection.getOpenQuantity().subtract(deliveredBeyondQc).subtract(held).subtract(coverage);
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
                                     List<SalesOrderLinkedDocumentResponse> documents,
                                     boolean showPrices) {
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
                documents,
                order.isDeliveryBaselineKnown(),
                order.isDeliveryBaselineKnown() ? null : SalesOrderResponse.LEGACY_ORDER,
                showPrices ? new SalesOrderCommercialResponse(
                        SalesOrderCommercialResponse.VND,
                        order.pricingVersion(),
                        (int) order.getLines().stream().filter(line -> line.getUnitPrice() == null).count())
                        : null);
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
