package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.domain.SupplyConversionTargetType;
import com.erp.manufacturing.module.planning.domain.SupplySuggestionConversion;
import com.erp.manufacturing.module.planning.dto.PurchaseOrderPlanningSource;
import com.erp.manufacturing.module.planning.repository.MrpRunRepository;
import com.erp.manufacturing.module.planning.repository.SupplySuggestionConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * G7a: the purchasing side of the G6b conversion ledger (rule C7 — purchasing never reads the
 * ledger repository itself). Reads the sources of purchase order lines and gives a cancelled order's
 * quota back.
 *
 * <p>No {@code @PreAuthorize}: the only caller is {@code PurchaseOrderService}, already authorised on
 * the order's plant. Not called from a controller.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderPlanningLinkService {

    private final SupplySuggestionConversionRepository conversionRepository;
    private final MrpRunRepository mrpRunRepository;

    /** Ledger rows behind each line, released ones included — two queries for any number of lines (C14). */
    @Transactional(readOnly = true)
    public Map<UUID, List<PurchaseOrderPlanningSource>> findSources(Collection<UUID> purchaseOrderLineIds) {
        if (purchaseOrderLineIds == null || purchaseOrderLineIds.isEmpty()) {
            return Map.of();
        }
        List<SupplySuggestionConversion> rows = conversionRepository
                .findByTargetTypeAndTargetLineIdInOrderByCreatedAtAscEntryNoAsc(
                        SupplyConversionTargetType.PURCHASE_ORDER, purchaseOrderLineIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MrpRun> runs = mrpRunRepository.findAllById(
                        rows.stream().map(SupplySuggestionConversion::getMrpRunId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(MrpRun::getMrpRunId, Function.identity()));
        return rows.stream().collect(Collectors.groupingBy(
                SupplySuggestionConversion::getTargetLineId,
                Collectors.mapping(row -> toSource(row, runs.get(row.getMrpRunId())), Collectors.toList())));
    }

    /** Orders of this set that still hold planning quota (FE {@code sourceLocked}) — one query (C14). */
    @Transactional(readOnly = true)
    public Set<UUID> findSourceLockedOrderIds(Collection<UUID> purchaseOrderIds) {
        if (purchaseOrderIds == null || purchaseOrderIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(conversionRepository.findHoldingTargetIds(
                SupplyConversionTargetType.PURCHASE_ORDER, purchaseOrderIds));
    }

    /**
     * Gives back the quota a cancelled purchase order held (FE Q12): each held ledger row is marked
     * released — never deleted — and every run it came from moves its conversion version on, so a
     * client holding the old version reloads before converting again. Runs in the caller's
     * transaction; a second call finds nothing held and changes nothing ("once").
     *
     * @return the number of rows released
     */
    @Transactional
    public int releasePurchaseOrder(UUID purchaseOrderId, String reason, Instant at, UUID by) {
        List<SupplySuggestionConversion> held = conversionRepository
                .findByTargetTypeAndTargetIdAndReleasedAtIsNull(SupplyConversionTargetType.PURCHASE_ORDER, purchaseOrderId);
        if (held.isEmpty()) {
            return 0;
        }
        // Same lock the conversion takes, in a fixed order so two cancels cannot deadlock.
        Set<UUID> runIds = held.stream().map(SupplySuggestionConversion::getMrpRunId)
                .collect(Collectors.toCollection(TreeSet::new));
        for (UUID runId : runIds) {
            mrpRunRepository.lockByMrpRunId(runId).ifPresent(run -> {
                run.advanceConversionVersion();
                mrpRunRepository.save(run);
            });
        }
        held.forEach(row -> row.release(reason, at, by));
        conversionRepository.saveAll(held);
        return held.size();
    }

    private static PurchaseOrderPlanningSource toSource(SupplySuggestionConversion row, MrpRun run) {
        return new PurchaseOrderPlanningSource(
                row.getTargetLineId(),
                row.getConversionId(),
                row.getMrpRunId(),
                run == null ? null : run.getCode(),
                row.getRequirementLineId(),
                row.getSupplySuggestionId(),
                run == null ? null : run.getSourceVersion(),
                row.getQuantity(),
                row.getCreatedAt(),
                row.getReleasedAt(),
                row.getReleaseReason());
    }
}
