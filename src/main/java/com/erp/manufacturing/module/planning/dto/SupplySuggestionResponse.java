package com.erp.manufacturing.module.planning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param supplyType {@code MAKE} / {@code BUY} (spec §2.4). F5-B renamed this from
 *        {@code suggestionType}; the persisted enum is still {@code WORK_ORDER} /
 *        {@code PURCHASE_REQUISITION}.
 * @param messages {@code PlanningMessageCode} names explaining {@code exceptionState}.
 * @param convertedWorkOrderId set only once a MAKE suggestion has become a work order, so the
 *        frontend can block a second convert.
 */
public record SupplySuggestionResponse(
        UUID supplySuggestionId,
        UUID mrpRunId,
        UUID requirementLineId,
        UUID companyId,
        String companyCode,
        UUID plantId,
        String plantCode,
        UUID warehouseId,
        String warehouseCode,
        UUID itemId,
        String itemSku,
        String itemName,
        /** Unit of measure of the item (spec §2.4 "Proposal"). */
        String uom,
        String supplyType,
        BigDecimal suggestedQuantity,
        LocalDate neededByDate,
        LocalDate suggestedOrderDate,
        /**
         * Routing frozen onto the proposal when the run executed (spec §2.4). Null for BUY, and for
         * a MAKE proposal blocked by {@code MISSING_ROUTING}.
         */
        String sourceRoutingCode,
        String sourceRoutingVersion,
        String status,
        String exceptionState,
        List<String> messages,
        String decisionNote,
        String convertedReferenceType,
        UUID convertedReferenceId,
        UUID convertedWorkOrderId,
        Instant createdAt,
        Instant updatedAt
) {}
