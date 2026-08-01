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

import java.util.UUID;

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
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive item cannot be used: " + itemId);
        }
        return item;
    }

    @Transactional(readOnly = true)
    public InventoryLot getLotForItem(Item item, UUID lotId) {
        InventoryLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Inventory lot", lotId));
        if (!lot.getItem().getItemId().equals(item.getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot does not belong to item: " + item.getItemId());
        }
        return lot;
    }
}
