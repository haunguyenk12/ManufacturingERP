package com.erp.manufacturing.module.sales.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.sales.domain.CommercialCommand;
import com.erp.manufacturing.module.sales.domain.SalesOrder;
import com.erp.manufacturing.module.sales.domain.SalesOrderLine;
import com.erp.manufacturing.module.sales.domain.SalesOrderPriceEvent;
import com.erp.manufacturing.module.sales.domain.SalesOrderStatus;
import com.erp.manufacturing.module.sales.dto.CommercialCommandResult;
import com.erp.manufacturing.module.sales.dto.SalesOrderCommercialPricesRequest;
import com.erp.manufacturing.module.sales.dto.SalesOrderLinePriceRequest;
import com.erp.manufacturing.module.sales.repository.SalesOrderPriceEventRepository;
import com.erp.manufacturing.module.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G10a (FE R02, Q6): filling missing prices on a confirmed sales order. A DRAFT changes its prices with the draft
 * itself ({@code PATCH /{id}/draft}); once confirmed, a missing price can be set exactly once and a known price —
 * zero included — is locked ({@code PRICE_LOCKED}). Every price set is a {@link SalesOrderPriceEvent} and moves the
 * order's {@code pricingVersion}; the whole request is all or nothing.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderPricingService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderPriceEventRepository priceEventRepository;
    private final CommercialIdempotency idempotency;

    @Transactional
    @PreAuthorize("@salesPermissionGuard.hasOrderAccess(authentication, 'PERM_SALES_PRICE_MANAGE', #salesOrderId)")
    @Auditable(action = AuditAction.SALES_ORDER_PRICES_SET, entityType = "SalesOrder", entityIdExpression = "#salesOrderId.toString()")
    public CommercialCommandResult setPrices(UUID salesOrderId, SalesOrderCommercialPricesRequest request, String rawKey) {
        String key = idempotency.key(rawKey);
        CommercialIdempotency.Fingerprint fingerprint = new CommercialIdempotency.Fingerprint(salesOrderId, request);
        if (idempotency.replay(key, CommercialCommand.SALES_PRICE_SET, fingerprint).isPresent()) {
            return result(findOrder(salesOrderId));
        }
        SalesOrder order = findOrder(salesOrderId);
        if (!Objects.equals(order.getVersion(), request.expectedVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Sales order version is " + order.getVersion() + ", not " + request.expectedVersion() + "; reload it");
        }
        if (order.isDraft() || order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Prices of a DRAFT order are set through the draft; a CANCELLED order cannot be priced");
        }
        Map<UUID, SalesOrderLine> lines = order.getLines().stream()
                .collect(Collectors.toMap(SalesOrderLine::getSalesOrderLineId, Function.identity()));
        List<SalesOrderLine> targets = validate(request.prices(), lines);

        order.bumpPricingRevision();
        // Lines are an inverse collection: dirty the header so its version moves (B112) — and do it before the first
        // flush below, or the header is updated twice and the version moves by two (B212; found by the G10 smoke).
        order.setUpdatedAt(Instant.now());
        List<SalesOrderPriceEvent> events = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            SalesOrderLine line = targets.get(i);
            line.setUnitPrice(request.prices().get(i).unitPrice());
            events.add(SalesOrderPriceEvent.builder()
                    .salesOrderId(order.getSalesOrderId())
                    .salesOrderLineId(line.getSalesOrderLineId())
                    .previousUnitPrice(null)
                    .unitPrice(line.getUnitPrice())
                    .pricingRevision(order.getPricingRevision())
                    .build());
        }
        priceEventRepository.saveAll(events);
        idempotency.record(key, CommercialCommand.SALES_PRICE_SET, order.getSalesOrderId(), fingerprint);
        return result(salesOrderRepository.saveAndFlush(order));
    }

    /**
     * Every line must be unique, on this order and still missing its price. All field errors are reported together
     * before anything changes (rule C9). Returns the lines in request order.
     */
    private static List<SalesOrderLine> validate(List<SalesOrderLinePriceRequest> prices, Map<UUID, SalesOrderLine> lines) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> locked = new LinkedHashMap<>();
        Set<UUID> seen = new HashSet<>();
        List<SalesOrderLine> targets = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            UUID lineId = prices.get(i).salesOrderLineId();
            SalesOrderLine line = lines.get(lineId);
            if (!seen.add(lineId)) {
                errors.put("prices[" + i + "].salesOrderLineId", "appears more than once");
            } else if (line == null) {
                errors.put("prices[" + i + "].salesOrderLineId", "is not a line of this sales order");
            } else if (line.getUnitPrice() != null) {
                locked.put("prices[" + i + "].unitPrice", "the line already has a price; a known price is locked");
            }
            targets.add(line);
        }
        if (!errors.isEmpty()) {
            throw ExceptionFactory.withErrors(ValidationErrorCode.INVALID_INPUT, errors);
        }
        if (!locked.isEmpty()) {
            throw ExceptionFactory.withErrors(BusinessErrorCode.PRICE_LOCKED, locked);
        }
        return targets;
    }

    private SalesOrder findOrder(UUID salesOrderId) {
        return salesOrderRepository.findWithDetailsBySalesOrderId(salesOrderId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Sales order", salesOrderId));
    }

    private static CommercialCommandResult result(SalesOrder order) {
        return new CommercialCommandResult(order.getSalesOrderId(), CommercialCommandResult.SALES_PRICE,
                order.getVersion(), order.pricingVersion());
    }
}
