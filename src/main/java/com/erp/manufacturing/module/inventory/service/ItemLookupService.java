package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemLookupService {

    private final ItemRepository itemRepository;
    private final InventoryLotRepository lotRepository;

    @Transactional(readOnly = true)
    public Item getItem(UUID itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", itemId));
    }

    @Transactional(readOnly = true)
    public Item getActiveItem(UUID itemId) {
        Item item = getItem(itemId);
        if (!item.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive item cannot be used: " + itemId);
        }
        return item;
    }

    /**
     * Resolves many item codes at once, in a single query (rule C14).
     *
     * <p>Entry point for callers that hold a list of codes rather than identifiers — the spreadsheet
     * importer being the first one. Codes absent from the company are simply missing from the map;
     * this method never throws for an unknown code, because the caller has to report the miss against
     * the row it came from rather than abort the whole file.
     *
     * @return code (exactly as stored) → item, empty when {@code codes} is empty
     */
    @Transactional(readOnly = true)
    public Map<String, Item> findItemsByCode(UUID companyId, Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return itemRepository.findByCompanyCompanyIdAndCodeIn(companyId, codes).stream()
                .collect(Collectors.toMap(Item::getCode, Function.identity()));
    }

    @Transactional(readOnly = true)
    public InventoryLot getLotForItem(Item item, UUID lotId) {
        InventoryLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Inventory lot", lotId));
        if (!lot.getItem().getItemId().equals(item.getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Lot does not belong to item: " + item.getItemId());
        }
        return lot;
    }
}
