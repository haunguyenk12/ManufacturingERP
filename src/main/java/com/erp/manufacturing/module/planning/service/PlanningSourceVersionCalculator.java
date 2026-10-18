package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.planning.domain.MrpRun;
import com.erp.manufacturing.module.planning.repository.MrpRequirementLineRepository;
import com.erp.manufacturing.module.planning.repository.MrpRunDemandRepository;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSource;
import com.erp.manufacturing.module.sales.service.SalesOrderPlanningSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The opaque {@code sourceVersion} of a commercial run (G6b, FE R12/A26): sha-256 over what the run
 * was computed from and what a conversion relies on.
 *
 * <ul>
 *   <li>each source sales order line: item, ordered quantity, due date, whether its order is still
 *       plannable, and the stock held for it;</li>
 *   <li>each manufacturable item of the run: its ACTIVE BOM and its ACTIVE routing.</li>
 * </ul>
 *
 * <p>Deliberately <em>not</em> included: free stock and open supply (they move all day; including
 * them would make every run stale within minutes), and work order coverage / QC accepted quantity
 * (the run's own conversions change them, and FE asks that an own conversion does not stale the run).
 * Converting therefore re-checks the sales order line's uncovered quantity separately (B181).
 */
@Component
@RequiredArgsConstructor
public class PlanningSourceVersionCalculator {

    /** Item types that can carry a BOM — same rule as {@code MrpCalculationService.canHaveBom}. */
    public static final List<ItemType> MANUFACTURABLE_TYPES = List.of(ItemType.WIP, ItemType.FINISHED_GOOD);

    private final SalesOrderPlanningSourceService salesOrderPlanningSourceService;
    private final BomLookupService bomLookupService;
    private final RoutingLookupService routingLookupService;
    private final MrpRunDemandRepository mrpRunDemandRepository;
    private final MrpRequirementLineRepository requirementLineRepository;

    /**
     * The version of a stored run's sources as they are <em>now</em>: the same inputs the run used —
     * its sales order lines (from its demand snapshot) and the manufacturable items of its
     * requirement lines — read again. Equal to {@code run.getSourceVersion()} ⇔ nothing changed.
     */
    public String computeCurrent(MrpRun run) {
        List<UUID> lineIds = mrpRunDemandRepository
                .findDemandReferenceIds(run.getMrpRunId(), PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE)
                .stream()
                .map(UUID::fromString)
                .toList();
        List<UUID> itemIds = requirementLineRepository.findDistinctItemIdsByRunAndTypes(
                run.getMrpRunId(), MANUFACTURABLE_TYPES);
        return compute(run.getCompany().getCompanyId(), lineIds, itemIds);
    }

    public String compute(UUID companyId,
                          Collection<UUID> salesOrderLineIds,
                          Collection<UUID> manufacturableItemIds) {
        Map<UUID, SalesOrderPlanningSource> sources =
                salesOrderPlanningSourceService.findSources(salesOrderLineIds);
        Map<UUID, BomHeader> boms = manufacturableItemIds.isEmpty()
                ? Map.of() : bomLookupService.findActiveBoms(companyId, manufacturableItemIds);
        Map<UUID, RoutingLookupService.RoutingSummary> routings = manufacturableItemIds.isEmpty()
                ? Map.of() : routingLookupService.findActiveRoutingSummaries(companyId, manufacturableItemIds);

        StringBuilder canonical = new StringBuilder();
        salesOrderLineIds.stream().distinct().sorted().forEach(lineId -> {
            SalesOrderPlanningSource source = sources.get(lineId);
            canonical.append("SO|").append(lineId).append('|');
            if (source == null) {
                canonical.append("MISSING\n");
                return;
            }
            canonical.append(source.itemId()).append('|')
                    .append(plain(source.orderedQuantity())).append('|')
                    .append(source.dueDate()).append('|')
                    .append(source.planningEligible()).append('|')
                    .append(plain(source.heldStockQuantity())).append('\n');
        });
        manufacturableItemIds.stream().distinct().sorted().forEach(itemId -> {
            BomHeader bom = boms.get(itemId);
            RoutingLookupService.RoutingSummary routing = routings.get(itemId);
            canonical.append("ITEM|").append(itemId)
                    .append("|BOM|").append(bom == null ? "-" : bom.getBomId())
                    .append("|RT|").append(routing == null ? "-" : routing.routingId())
                    .append('\n');
        });
        return sha256(canonical.toString());
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
