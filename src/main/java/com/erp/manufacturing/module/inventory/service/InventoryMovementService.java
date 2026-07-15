package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.inventory.repository.StockMovementRepository;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 120;

    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLotRepository lotRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;

    @Transactional(readOnly = true)
    public boolean hasMovementForIdempotencyKey(String idempotencyKey) {
        return movementRepository.findByIdempotencyKey(normalizeIdempotencyKey(idempotencyKey)).isPresent();
    }

    @Transactional
    public InventoryMovementResult receive(InventoryReceiveCommand command, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing = movementRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot lot = resolveReceiveLot(item, command.lotId(), command.lotCode());
        BigDecimal quantity = requirePositive(command.quantity());

        StockBalance balance = findOrCreateBalance(item, warehouse, lot);
        balance.increase(quantity);
        balanceRepository.save(balance);

        StockMovement movement = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    @Transactional
    public InventoryMovementResult issue(InventoryIssueCommand command, String idempotencyKey) {
        return issueInternal(command, idempotencyKey, false);
    }

    /**
     * Creates a REVERSAL movement that undoes a previously posted goods receipt line.
     * Used by Cancel Goods Receipt to restore stock balance.
     *
     * @param itemId        item whose stock to reverse
     * @param warehouseId   warehouse where the original receipt was posted
     * @param lotId         lot (nullable for non-lot-tracked items)
     * @param quantity      positive quantity to reverse (will be deducted from stock)
     * @param referenceType reference type (e.g. "GOODS_RECEIPT_CANCEL")
     * @param referenceId   reference id (e.g. goodsReceiptId as string)
     * @param idempotencyKey must be unique per reversal line
     */
    @Transactional
    public InventoryMovementResult reverseReceive(UUID itemId, UUID warehouseId, UUID lotId,
                                                   BigDecimal quantity,
                                                   String referenceType, String referenceId,
                                                   String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing = movementRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(itemId);
        Warehouse warehouse = findActiveWarehouse(warehouseId);
        ensureSameCompany(item, warehouse);

        // Resolve lot without the canIssue() check — reversal of a RECEIVE must be allowed
        // even if the lot is now HOLD/REJECTED, to maintain ledger accuracy.
        InventoryLot lot = null;
        if (item.isLotTracked()) {
            if (lotId == null) {
                throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                        "Lot-tracked item requires a lotId for reversal: " + itemId);
            }
            lot = findLotForItem(item, lotId);
        }

        BigDecimal positiveQty = requirePositive(quantity);
        StockBalance balance = findBalance(item, warehouse, lot);
        // Use ensureSufficientStock (total qty) not available qty, because reserved qty
        // should not block reversal of a previously posted receipt.
        ensureSufficientStock(balance, positiveQty);
        balance.decrease(positiveQty);
        balanceRepository.save(balance);

        StockMovement movement = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(MovementType.REVERSAL)
                .direction(MovementDirection.OUT)
                .quantity(positiveQty)
                .referenceType(trimToNull(referenceType))
                .referenceId(trimToNull(referenceId))
                .idempotencyKey(normalizedKey)
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    @Transactional
    public InventoryMovementResult issueReserved(InventoryIssueCommand command, String idempotencyKey) {
        return issueInternal(command, idempotencyKey, true);
    }

    private InventoryMovementResult issueInternal(InventoryIssueCommand command,
                                                  String idempotencyKey,
                                                  boolean consumeReserved) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing = movementRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot lot = resolveExistingLotForOutbound(item, command.lotId(), command.lotCode());
        BigDecimal quantity = requirePositive(command.quantity());

        StockBalance balance = findBalance(item, warehouse, lot);
        if (consumeReserved) {
            ensureSufficientReservedStock(balance, quantity);
            balance.consumeReserved(quantity);
        } else {
            ensureSufficientAvailableStock(balance, quantity);
            balance.decrease(quantity);
        }
        balanceRepository.save(balance);

        StockMovement movement = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(MovementType.ISSUE)
                .direction(MovementDirection.OUT)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    @Transactional
    public InventoryMovementResult adjust(InventoryAdjustCommand command, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing = movementRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot lot = resolveExistingLotForAdjustment(item, command.lotId(), command.lotCode());

        BigDecimal delta = command.quantityDelta();
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "Adjustment quantity must be non-zero");
        }

        boolean inbound = delta.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal quantity = delta.abs();
        StockBalance balance = inbound
                ? findOrCreateBalance(item, warehouse, lot)
                : findBalance(item, warehouse, lot);
        if (inbound) {
            balance.increase(quantity);
        } else {
            ensureSufficientAvailableStock(balance, quantity);
            balance.decrease(quantity);
        }
        balanceRepository.save(balance);

        StockMovement movement = StockMovement.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(inbound ? MovementType.ADJUST_IN : MovementType.ADJUST_OUT)
                .direction(inbound ? MovementDirection.IN : MovementDirection.OUT)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    private Item findActiveItem(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", itemId));
        if (!item.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive item cannot be used in stock movement: " + itemId);
        }
        return item;
    }

    private Warehouse findActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        if (!warehouse.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive warehouse cannot be used in stock movement: " + warehouseId);
        }
        return warehouse;
    }

    private void ensureSameCompany(Item item, Warehouse warehouse) {
        UUID itemCompanyId = item.getCompany().getCompanyId();
        UUID warehouseCompanyId = warehouse.getPlant().getCompany().getCompanyId();
        if (!itemCompanyId.equals(warehouseCompanyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Item and warehouse must belong to the same company");
        }
    }

    private InventoryLot resolveReceiveLot(Item item, UUID lotId, String lotCode) {
        if (!item.isLotTracked()) {
            ensureNoLotProvided(lotId, lotCode);
            return null;
        }
        if (lotId != null) {
            return findLotForItem(item, lotId);
        }
        String normalizedLotCode = requireLotCode(lotCode);
        return lotRepository.findByItemItemIdAndLotCode(item.getItemId(), normalizedLotCode)
                .orElseGet(() -> lotRepository.save(InventoryLot.builder()
                        .item(item)
                        .lotCode(normalizedLotCode)
                        .status(LotStatus.AVAILABLE)
                        .receivedAt(Instant.now())
                        .build()));
    }

    private InventoryLot resolveExistingLotForOutbound(Item item, UUID lotId, String lotCode) {
        InventoryLot lot = resolveExistingLot(item, lotId, lotCode);
        if (lot != null && !lot.canIssue()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot cannot be issued in status: " + lot.getStatus());
        }
        return lot;
    }

    private InventoryLot resolveExistingLotForAdjustment(Item item, UUID lotId, String lotCode) {
        return resolveExistingLot(item, lotId, lotCode);
    }

    private InventoryLot resolveExistingLot(Item item, UUID lotId, String lotCode) {
        if (!item.isLotTracked()) {
            ensureNoLotProvided(lotId, lotCode);
            return null;
        }
        if (lotId != null) {
            return findLotForItem(item, lotId);
        }
        String normalizedLotCode = requireLotCode(lotCode);
        return lotRepository.findByItemItemIdAndLotCode(item.getItemId(), normalizedLotCode)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Inventory lot", normalizedLotCode));
    }

    private InventoryLot findLotForItem(Item item, UUID lotId) {
        InventoryLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Inventory lot", lotId));
        if (!lot.getItem().getItemId().equals(item.getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot does not belong to item: " + item.getItemId());
        }
        return lot;
    }

    private StockBalance findOrCreateBalance(Item item, Warehouse warehouse, InventoryLot lot) {
        return findBalanceOptional(item, warehouse, lot).orElseGet(() -> StockBalance.builder()
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .quantity(BigDecimal.ZERO)
                .build());
    }

    private StockBalance findBalance(Item item, Warehouse warehouse, InventoryLot lot) {
        return findBalanceOptional(item, warehouse, lot)
                .orElseThrow(() -> ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK,
                        "No stock balance exists for this item, warehouse, and lot"));
    }

    private Optional<StockBalance> findBalanceOptional(Item item, Warehouse warehouse, InventoryLot lot) {
        if (lot == null) {
            return balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                    item.getItemId(), warehouse.getWarehouseId());
        }
        return balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                item.getItemId(), warehouse.getWarehouseId(), lot.getLotId());
    }

    private void ensureSufficientStock(StockBalance balance, BigDecimal quantity) {
        if (balance.getQuantity().compareTo(quantity) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private void ensureSufficientAvailableStock(StockBalance balance, BigDecimal quantity) {
        if (balance.availableQuantity().compareTo(quantity) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private void ensureSufficientReservedStock(StockBalance balance, BigDecimal quantity) {
        if (balance.getQuantity().compareTo(quantity) < 0 || balance.getReservedQuantity().compareTo(quantity) < 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.INSUFFICIENT_STOCK);
        }
    }

    private void ensureNoLotProvided(UUID lotId, String lotCode) {
        if (lotId != null || StringUtils.hasText(lotCode)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot can only be provided for lot-tracked items");
        }
    }

    private String requireLotCode(String lotCode) {
        if (!StringUtils.hasText(lotCode)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Lot-tracked item requires a lot code or lot id");
        }
        return lotCode.trim();
    }

    private BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "Quantity must be greater than zero");
        }
        return quantity;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw ExceptionFactory.custom(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Idempotency-Key header is required");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw ExceptionFactory.custom(ValidationErrorCode.FIELD_TOO_LONG,
                    "Idempotency-Key must be at most " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
