package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entry point for other modules that need to render a lot id as its human-readable code (rule C7).
 *
 * <p>Exists because a document can reference a lot by id long before it is linked to one: an
 * Over-BOM material request stores {@code requested_lot_id} while it waits for approval, and the
 * manager deciding it has to see which physical lot will be consumed — a UUID is not a decision aid.
 *
 * <p>Batch-oriented on purpose: a page of documents resolves its lot codes in one query, not one per
 * row (rule C14). Same shape and same rationale as {@code UserLookupService.findUsernames}. No
 * {@code @PreAuthorize} — a lot code attached to a document the caller is already authorized to read
 * carries no additional privilege.
 */
@Service
@RequiredArgsConstructor
public class InventoryLotLookupService {

    private final InventoryLotRepository lotRepository;

    /** Ids with no matching lot are simply absent from the map — callers render null. */
    @Transactional(readOnly = true)
    public Map<UUID, String> findLotCodes(Collection<UUID> lotIds) {
        Set<UUID> ids = lotIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return lotRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InventoryLot::getLotId, InventoryLot::getLotCode));
    }
}
