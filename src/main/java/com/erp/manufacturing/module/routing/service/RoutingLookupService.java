package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entry point for other modules that need routing master data (rule C7) — {@code workorder} uses it
 * to freeze a routing snapshot onto a work order. Callers are already authorized on their own
 * aggregate, so no {@code @PreAuthorize} here: this is read-only master data lookup, mirroring
 * {@code BomLookupService}.
 */
@Service
@RequiredArgsConstructor
public class RoutingLookupService {

    private final RoutingHeaderRepository routingHeaderRepository;

    @Transactional(readOnly = true)
    public Optional<RoutingHeader> findActiveRouting(UUID companyId, UUID itemId) {
        return routingHeaderRepository.findWithOperationsByCompanyCompanyIdAndItemItemIdAndStatus(
                companyId, itemId, RoutingStatus.ACTIVE);
    }

    /**
     * @throws com.erp.manufacturing.common.exception.AppException {@code MISSING_ROUTING} when the
     *         item has no {@code ACTIVE} routing (spec §8.1: proposal is BLOCKED, no work order).
     */
    @Transactional(readOnly = true)
    public RoutingHeader getActiveRouting(UUID companyId, UUID itemId) {
        return findActiveRouting(companyId, itemId)
                .orElseThrow(() -> ExceptionFactory.businessRule(BusinessErrorCode.MISSING_ROUTING,
                        "No ACTIVE routing for item " + itemId));
    }

    /**
     * The ACTIVE routing of each given item, keyed by item id. MRP needs this per BOM level — to
     * flag MAKE proposals as {@code BLOCKED}/{@code MISSING_ROUTING} <em>and</em> to stamp the
     * routing code/version onto the proposal (spec §2.4) — so it must be one query rather than one
     * per item (rule C14); mirrors {@code BomLookupService.findActiveBoms}.
     *
     * <p>An absent key means "no ACTIVE routing", which is the only question the blocking rules ask,
     * so callers that used to take a {@code Set<UUID>} read {@code keySet()} instead of issuing a
     * second query.
     */
    @Transactional(readOnly = true)
    public Map<UUID, RoutingSummary> findActiveRoutingSummaries(UUID companyId, Collection<UUID> itemIds) {
        if (companyId == null || itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        return routingHeaderRepository
                .findByCompanyCompanyIdAndItemItemIdInAndStatus(companyId, itemIds, RoutingStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        routing -> routing.getItem().getItemId(),
                        routing -> new RoutingSummary(
                                routing.getRoutingId(), routing.getCode(), routing.getRoutingVersion()),
                        // uk_routings_one_active_per_item makes this unreachable; keeping the first
                        // wins rather than letting toMap throw on data that predates that index.
                        (first, second) -> first));
    }

    /**
     * What a consumer outside {@code routing} needs from an ACTIVE routing header. Deliberately not
     * the entity: {@code version} here is {@code RoutingHeader.routingVersion} (a String business
     * version), never {@code BaseEntity.version} (the optimistic-locking counter).
     */
    public record RoutingSummary(UUID routingId, String code, String version) {}
}
