package com.erp.manufacturing.module.inventory.service;

import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.repository.InventoryLotRepository;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.repository.SerialNumberRepository;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    /** {@code reference_type} of the ADJUST_IN/ADJUST_OUT row written by a stock count (G4-3). */
    public static final String COUNT_REFERENCE_TYPE = "STOCK_COUNT";

    private final ItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryLotRepository lotRepository;
    private final SerialNumberRepository serialNumberRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockMovementRepository movementRepository;
    private final IdempotencySupport idempotency;
    private final TraceIdProvider traceIdProvider;

    @Transactional
    public InventoryMovementResult receive(InventoryReceiveCommand command, String idempotencyKey) {
        return receive(command, idempotencyKey, LotStatus.AVAILABLE);
    }

    /**
     * Receives stock under the requested initial quality state. For a new lot, that state is carried
     * by the lot status. For output with neither a lot nor a serial, {@link LotStatus#HOLD} is carried
     * by {@code StockBalance.qualityHoldQuantity}. Production receipt approval uses HOLD so either
     * tracking shape remains unavailable until quality releases it.
     * <p>
     * For lot-tracked items the status applies to <b>newly created lots only</b> — an existing lot
     * keeps its current status. The explicit balance hold applies only when neither lot nor serial
     * exists.
     */
    @Transactional
    public InventoryMovementResult receive(InventoryReceiveCommand command,
                                           String idempotencyKey,
                                           LotStatus initialStatusForNewLot) {
        return receive(command, idempotencyKey, initialStatusForNewLot, true);
    }

    public InventoryMovementResult receive(InventoryReceiveCommand command,
                                           String idempotencyKey,
                                           LotStatus initialStatusForNewLot,
                                           boolean allowImplicitLotReuse) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing =
                findReplay(normalizedKey, MovementType.RECEIVE);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot lot = resolveReceiveLot(
                item, command.lotId(), command.lotCode(), command.expiresAt(),
                initialStatusForNewLot, allowImplicitLotReuse);
        BigDecimal quantity = requirePositive(command.quantity());
        SerialNumber serial = resolveReceiveSerial(item, command.serialId(), command.serialCode(), quantity);

        StockBalance balance = findOrCreateBalance(item, warehouse, lot);
        balance.increase(quantity);
        if (initialStatusForNewLot == LotStatus.HOLD && lot == null && serial == null) {
            balance.holdForQuality(quantity);
        }
        balanceRepository.save(balance);

        StockMovement movement = StockMovement.builder()
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .serial(serial)
                .movementType(MovementType.RECEIVE)
                .direction(MovementDirection.IN)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(command))
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    /**
     * Releases non-lot, non-serial production output from its quality hold. No stock movement is
     * written because on-hand quantity does not move; the Production Receipt QC decision is the
     * authoritative audit event. This method only changes how much of the existing balance may be
     * reserved/issued/planned.
     */
    @Transactional
    public void releaseQualityHold(UUID itemId, UUID warehouseId, BigDecimal quantity) {
        Item item = findActiveItem(itemId);
        Warehouse warehouse = findActiveWarehouse(warehouseId);
        ensureSameCompany(item, warehouse);
        BigDecimal positiveQuantity = requirePositive(quantity);
        StockBalance balance = findBalance(item, warehouse, null);
        if (balance.getQualityHoldQuantity().compareTo(positiveQuantity) < 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Insufficient quality-held stock to release");
        }
        balance.releaseQualityHold(positiveQuantity);
        balanceRepository.save(balance);
    }

    @Transactional
    public InventoryMovementResult issue(InventoryIssueCommand command, String idempotencyKey) {
        return issueInternal(command, idempotencyKey, false, false);
    }

    /**
     * {@code POST /v1/inventory/issue} (G4): the same free-stock issue as {@link #issue}, but a lot past
     * its {@code expiresAt} is refused. Kept apart from {@link #issue} on purpose - work-order material
     * issue and FEFO reservation still treat an AVAILABLE lot as issuable regardless of its date; moving
     * that rule into planning is a separate decision (NEXT_PHASE_PLAN.md section 11, user choice 2026-10-15).
     */
    @Transactional
    public InventoryMovementResult issueManual(InventoryIssueCommand command, String idempotencyKey) {
        return issueInternal(command, idempotencyKey, false, true);
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
        Optional<StockMovement> existing =
                findReplay(normalizedKey, MovementType.REVERSAL);
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
                .traceId(traceIdProvider.currentTraceId())
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

    /**
     * G7c: splits part of a lot into a new child lot in the same warehouse — a purchase QC decision
     * moves the accepted quantity into an {@code AVAILABLE} child and the rejected quantity into a
     * {@code REJECTED} child, leaving what is still pending on the parent. Writes a
     * {@link MovementType#LOT_SPLIT_OUT} on the parent and a {@link MovementType#LOT_SPLIT_IN} on the
     * child under the same key; on-hand of the item in the warehouse is unchanged.
     *
     * <p>Only unreserved quantity can be split: the parent is on hold, so nothing can have reserved it.
     * The child code must be new for the item, ignoring case (FE Q28) — the caller picks it.
     */
    @Transactional
    public LotSplitResult splitLot(LotSplitCommand command, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> replayIn = findReplay(normalizedKey, MovementType.LOT_SPLIT_IN);
        if (replayIn.isPresent()) {
            StockMovement out = findReplay(normalizedKey, MovementType.LOT_SPLIT_OUT).orElse(null);
            return new LotSplitResult(replayIn.get().getLot(), out, replayIn.get());
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot parent = findLotForItem(item, command.parentLotId());
        BigDecimal quantity = requirePositive(command.quantity());
        String childCode = requireLotCode(command.childLotCode());
        if (lotRepository.existsByItemItemIdAndLotCodeIgnoreCase(item.getItemId(), childCode)) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS,
                    "Lot code already exists for this item: " + childCode);
        }

        StockBalance source = findBalance(item, warehouse, parent);
        if (source.getQuantity().subtract(source.getReservedQuantity()).compareTo(quantity) < 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.INSUFFICIENT_STOCK,
                    "Not enough unreserved stock in lot " + parent.getLotCode() + " to split " + quantity);
        }
        InventoryLot child = lotRepository.save(InventoryLot.builder()
                .item(item)
                .lotCode(childCode)
                .status(command.childStatus())
                .receivedAt(parent.getReceivedAt())
                .expiresAt(parent.getExpiresAt())
                .parentLot(parent)
                .sourceReferenceType(trimToNull(command.sourceReferenceType()))
                .sourceReferenceId(command.sourceReferenceId())
                .build());
        source.decrease(quantity);
        StockBalance destination = findOrCreateBalance(item, warehouse, child);
        destination.increase(quantity);
        balanceRepository.save(source);
        balanceRepository.save(destination);

        String referenceType = trimToNull(command.sourceReferenceType());
        String referenceId = command.sourceReferenceId() == null ? null : command.sourceReferenceId().toString();
        String payloadHash = idempotency.payloadHash(command);
        Instant now = Instant.now();
        StockMovement out = movementRepository.save(StockMovement.builder()
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(parent)
                .movementType(MovementType.LOT_SPLIT_OUT)
                .direction(MovementDirection.OUT)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(referenceType)
                .referenceId(referenceId)
                .idempotencyKey(normalizedKey)
                .payloadHash(payloadHash)
                .createdAt(now)
                .build());
        StockMovement in = movementRepository.save(StockMovement.builder()
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(child)
                .movementType(MovementType.LOT_SPLIT_IN)
                .direction(MovementDirection.IN)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(referenceType)
                .referenceId(referenceId)
                .idempotencyKey(normalizedKey)
                .payloadHash(payloadHash)
                .createdAt(now)
                .build());
        return new LotSplitResult(child, out, in);
    }

    /**
     * Applies a QC disposition to a lot: the lot moves to {@code newStatus} and a
     * {@link MovementType#LOT_STATUS_CHANGE} row is appended to the ledger for traceability.
     * <p>
     * Deliberately does <b>not</b> touch {@link StockBalance}: the goods are already on hand, QC only
     * decides whether they may be used. Availability follows automatically because the availability
     * queries filter on lot status. The movement therefore carries
     * {@link MovementDirection#NONE} — it belongs to neither the inbound nor the outbound side.
     */
    @Transactional
    public InventoryMovementResult changeLotStatus(LotStatusChangeCommand command, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing =
                findReplay(normalizedKey, MovementType.LOT_STATUS_CHANGE);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        if (command.lotId() == null) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                    "A lot is required to change lot status");
        }
        InventoryLot lot = findLotForItem(item, command.lotId());
        BigDecimal quantity = requirePositive(command.quantity());

        lot.setStatus(command.newStatus());
        lotRepository.save(lot);

        StockMovement movement = StockMovement.builder()
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(MovementType.LOT_STATUS_CHANGE)
                .direction(MovementDirection.NONE)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(command))
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    @Transactional
    public InventoryMovementResult issueReserved(InventoryIssueCommand command, String idempotencyKey) {
        return issueInternal(command, idempotencyKey, true, false);
    }

    /**
     * G10b: a delivery's share taken from its own sales order hold — {@link #issueReserved}, but a lot past its
     * {@code expiresAt} is refused like {@link #issueManual} (a delivery refuses expired stock on both of its legs).
     */
    @Transactional
    public InventoryMovementResult issueReservedManual(InventoryIssueCommand command, String idempotencyKey) {
        return issueInternal(command, idempotencyKey, true, true);
    }

    /** G10b: one stock position as it is now, for a read-only figure (free quantity of a delivery source). */
    @Transactional(readOnly = true)
    public Optional<StockBalance> findPosition(StockPositionRef position) {
        return position.lotId() == null
                ? balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                        position.itemId(), position.warehouseId())
                : balanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                        position.itemId(), position.warehouseId(), position.lotId());
    }

    /**
     * G10b: takes a write lock on each stock position, in one stable order (item, warehouse, lot), before any of them
     * is read — so two deliveries over the same positions queue instead of both reading the same free quantity, and
     * cannot deadlock (same idea as B151). A position that does not exist is skipped: the issue that follows reports
     * the shortage. Call it before anything has loaded these balances into the persistence context (B194).
     */
    @Transactional
    public void lockPositions(java.util.Collection<StockPositionRef> positions) {
        positions.stream()
                .distinct()
                .sorted(java.util.Comparator.comparing((StockPositionRef p) -> p.itemId().toString())
                        .thenComparing(p -> p.warehouseId().toString())
                        .thenComparing(p -> p.lotId() == null ? "" : p.lotId().toString()))
                .forEach(p -> {
                    if (p.lotId() == null) {
                        balanceRepository.lockPositionWithoutLot(p.itemId(), p.warehouseId());
                    } else {
                        balanceRepository.lockPositionWithLot(p.itemId(), p.warehouseId(), p.lotId());
                    }
                });
    }

    /**
     * Both {@link #issue} and {@link #issueReserved} write {@link MovementType#ISSUE}, so they share a
     * single replay scope on purpose: both are "goods leaving the warehouse", and inventing a second
     * movement type just to separate two entry points would put an implementation detail in the ledger.
     */
    private InventoryMovementResult issueInternal(InventoryIssueCommand command,
                                                  String idempotencyKey,
                                                  boolean consumeReserved,
                                                  boolean rejectExpiredLot) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing =
                findReplay(normalizedKey, MovementType.ISSUE);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot lot = resolveExistingLotForOutbound(item, command.lotId(), command.lotCode());
        if (rejectExpiredLot) {
            ensureNotExpired(lot);
        }
        BigDecimal quantity = requirePositive(command.quantity());
        SerialNumber serial = resolveExistingSerialForOutbound(item, command.serialId(), quantity);

        StockBalance balance = findBalance(item, warehouse, lot);
        if (consumeReserved) {
            ensureSufficientReservedStock(balance, quantity);
            balance.consumeReserved(quantity);
        } else {
            ensureSufficientAvailableStock(balance, quantity);
            balance.decrease(quantity);
        }
        balanceRepository.save(balance);
        if (serial != null) {
            serial.setStatus(SerialStatus.ISSUED);
            serialNumberRepository.save(serial);
        }

        StockMovement movement = StockMovement.builder()
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .serial(serial)
                .movementType(MovementType.ISSUE)
                .direction(MovementDirection.OUT)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(command))
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    /**
     * An adjustment writes {@link MovementType#ADJUST_IN} <em>or</em> {@link MovementType#ADJUST_OUT}
     * depending on the sign of the delta, so the sign has to be known <b>before</b> the replay lookup —
     * that is why {@code quantityDelta} is read first here and not next to the balance update.
     * <p>
     * Consequence: a zero/absent delta now fails with {@code NEGATIVE_QUANTITY} <em>before</em> an
     * unknown item or warehouse fails with {@code RESOURCE_NOT_FOUND}. Reading both types instead
     * would keep the old ordering but leave the same key usable once as {@code ADJUST_IN} and once as
     * {@code ADJUST_OUT} — the exact hole this change closes.
     */
    @Transactional
    public InventoryMovementResult adjust(InventoryAdjustCommand command, String idempotencyKey) {
        return adjust(command, idempotencyKey, lot -> { });
    }

    /**
     * CF-1: {@code lotGuard} runs after the replay lookup (a replay returns the original movement even if the
     * lot is now guarded) and after the lot is resolved, before any balance changes.
     */
    @Transactional
    public InventoryMovementResult adjust(InventoryAdjustCommand command, String idempotencyKey,
                                         java.util.function.Consumer<InventoryLot> lotGuard) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);

        BigDecimal delta = command.quantityDelta();
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.NEGATIVE_QUANTITY,
                    "Adjustment quantity must be non-zero");
        }
        boolean inbound = delta.compareTo(BigDecimal.ZERO) > 0;
        MovementType movementType = inbound ? MovementType.ADJUST_IN : MovementType.ADJUST_OUT;

        Optional<StockMovement> existing = findReplay(normalizedKey, movementType);
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
            return new InventoryMovementResult(existing.get(), false);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        InventoryLot lot = resolveExistingLotForAdjustment(item, command.lotId(), command.lotCode());
        lotGuard.accept(lot);

        BigDecimal quantity = delta.abs();
        SerialNumber serial = resolveExistingSerialForAdjustment(item, command.serialId(), quantity);
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
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .serial(serial)
                .movementType(movementType)
                .direction(inbound ? MovementDirection.IN : MovementDirection.OUT)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(trimToNull(command.referenceType()))
                .referenceId(trimToNull(command.referenceId()))
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(command))
                .createdAt(Instant.now())
                .build();
        return new InventoryMovementResult(movementRepository.save(movement), true);
    }

    /**
     * Absolute stock count (G4-3, invariant B153). The position is locked, its {@code version} compared
     * with what the user looked at, and the delta derived from the locked quantity - never from the
     * client. A positive delta writes {@code ADJUST_IN}, a negative one {@code ADJUST_OUT}: a count is an
     * adjustment whose size the server computes, not a new kind of movement.
     * <p>
     * The replay lookup reads both adjustment types because the sign is only known after the lock. A
     * key once used on {@code POST /adjust} fails the payload check here ({@code IDEMPOTENCY_CONFLICT}).
     */
    @Transactional
    public InventoryCountResult count(InventoryCountCommand command, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<StockMovement> existing = findReplay(normalizedKey, MovementType.ADJUST_IN)
                .or(() -> findReplay(normalizedKey, MovementType.ADJUST_OUT));
        if (existing.isPresent()) {
            idempotency.ensureSamePayload(existing.get().getPayloadHash(), command);
            StockMovement movement = existing.get();
            BigDecimal previous = movement.getDirection() == MovementDirection.IN
                    ? command.countedQuantity().subtract(movement.getQuantity())
                    : command.countedQuantity().add(movement.getQuantity());
            return new InventoryCountResult(movement,
                    findBalance(movement.getItem(), movement.getWarehouse(), movement.getLot()), previous);
        }

        Item item = findActiveItem(command.itemId());
        Warehouse warehouse = findActiveWarehouse(command.warehouseId());
        ensureSameCompany(item, warehouse);
        if (item.isSerialTracked()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked items are not counted through this endpoint");
        }
        InventoryLot lot = resolveCountLot(item, command.lotId());
        StockBalance balance = (lot == null
                ? balanceRepository.lockPositionWithoutLot(item.getItemId(), warehouse.getWarehouseId())
                : balanceRepository.lockPositionWithLot(item.getItemId(), warehouse.getWarehouseId(), lot.getLotId()))
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND,
                        "No stock position exists for this item, warehouse and lot"));
        if (!Objects.equals(balance.getVersion(), command.expectedVersion())) {
            throw ExceptionFactory.custom(BusinessErrorCode.CONCURRENT_MODIFICATION,
                    "Stock position changed since it was read; reload and count again");
        }

        BigDecimal counted = command.countedQuantity();
        BigDecimal previous = balance.getQuantity();
        BigDecimal delta = counted.subtract(previous);
        if (delta.signum() == 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.NO_CHANGE,
                    "Counted quantity equals the current quantity; nothing to record");
        }
        if (counted.compareTo(balance.getReservedQuantity()) < 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.RESERVED_STOCK,
                    "Counted quantity is below the quantity reserved for work orders");
        }
        if (counted.compareTo(balance.getReservedQuantity().add(balance.getQualityHoldQuantity())) < 0) {
            throw ExceptionFactory.custom(BusinessErrorCode.STATE_CONFLICT,
                    "Counted quantity is below the quantity held for quality inspection");
        }

        boolean inbound = delta.signum() > 0;
        BigDecimal quantity = delta.abs();
        if (inbound) {
            balance.increase(quantity);
        } else {
            balance.decrease(quantity);
        }
        StockBalance saved = balanceRepository.saveAndFlush(balance);

        StockMovement movement = StockMovement.builder()
                .traceId(traceIdProvider.currentTraceId())
                .item(item)
                .warehouse(warehouse)
                .lot(lot)
                .movementType(inbound ? MovementType.ADJUST_IN : MovementType.ADJUST_OUT)
                .direction(inbound ? MovementDirection.IN : MovementDirection.OUT)
                .quantity(quantity)
                .reason(trimToNull(command.reason()))
                .referenceType(COUNT_REFERENCE_TYPE)
                .idempotencyKey(normalizedKey)
                .payloadHash(idempotency.payloadHash(command))
                .createdAt(Instant.now())
                .build();
        return new InventoryCountResult(movementRepository.save(movement), saved, previous);
    }

    private InventoryLot resolveCountLot(Item item, UUID lotId) {
        if (!item.isLotTracked()) {
            ensureNoLotProvided(lotId, null);
            return null;
        }
        if (lotId == null) {
            throw ExceptionFactory.custom(ValidationErrorCode.LOT_REQUIRED,
                    "A lot-tracked item is counted per lot; lotId is required");
        }
        InventoryLot lot = findLotForItem(item, lotId);
        if (!lot.canIssue()) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                    "Lot cannot be counted in status: " + lot.getStatus());
        }
        ensureNotExpired(lot);
        return lot;
    }

    /** G4 manual flows only (issue, count, transfer, status to AVAILABLE): expiry is judged on the server clock. */
    private void ensureNotExpired(InventoryLot lot) {
        if (lot != null && lot.getExpiresAt() != null && !lot.getExpiresAt().isAfter(Instant.now())) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
                    "Lot expired at " + lot.getExpiresAt());
        }
    }

    private Item findActiveItem(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Item", itemId));
        if (!item.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive item cannot be used in stock movement: " + itemId);
        }
        return item;
    }

    private Warehouse findActiveWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Warehouse", warehouseId));
        if (!warehouse.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive warehouse cannot be used in stock movement: " + warehouseId);
        }
        return warehouse;
    }

    private void ensureSameCompany(Item item, Warehouse warehouse) {
        UUID itemCompanyId = item.getCompany().getCompanyId();
        UUID warehouseCompanyId = warehouse.getPlant().getCompany().getCompanyId();
        if (!itemCompanyId.equals(warehouseCompanyId)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Item and warehouse must belong to the same company");
        }
    }

    /**
     * {@code expiresAt} (G4-4) is only accepted when this receipt <em>creates</em> the lot: an existing
     * lot already has an expiry, possibly in other warehouses, and a receipt must not rewrite it.
     */
    private InventoryLot resolveReceiveLot(Item item, UUID lotId, String lotCode, Instant expiresAt,
                                           LotStatus initialStatusForNewLot,
                                           boolean allowImplicitLotReuse) {
        if (!item.isLotTracked()) {
            ensureNoLotProvided(lotId, lotCode);
            ensureNoExpiryForExistingLot(expiresAt);
            return null;
        }
        if (lotId != null) {
            ensureNoExpiryForExistingLot(expiresAt);
            return findLotForItem(item, lotId);
        }
        String normalizedLotCode = requireLotCode(lotCode);
        Optional<InventoryLot> existing = lotRepository.findByItemItemIdAndLotCode(
                item.getItemId(), normalizedLotCode);
        if (existing.isPresent()) {
            if (!allowImplicitLotReuse) {
                throw ExceptionFactory.custom(BusinessErrorCode.LOT_CODE_ALREADY_EXISTS,
                        "Lot code already exists; send lotId for an explicit partial receipt");
            }
            ensureNoExpiryForExistingLot(expiresAt);
            return existing.get();
        }
        return lotRepository.save(InventoryLot.builder()
                        .item(item)
                        .lotCode(normalizedLotCode)
                        .status(initialStatusForNewLot)
                        .receivedAt(Instant.now())
                        .expiresAt(expiresAt)
                        .build());
    }

    private void ensureNoExpiryForExistingLot(Instant expiresAt) {
        if (expiresAt != null) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "expiresAt can only be set when the receipt creates a new lot");
        }
    }

    private InventoryLot resolveExistingLotForOutbound(Item item, UUID lotId, String lotCode) {
        InventoryLot lot = resolveExistingLot(item, lotId, lotCode);
        if (lot != null && !lot.canIssue()) {
            throw ExceptionFactory.custom(BusinessErrorCode.LOT_NOT_ELIGIBLE,
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
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Lot does not belong to item: " + item.getItemId());
        }
        return lot;
    }

    /**
     * A serial is never "received into" an existing row the way a lot bucket can be — it always
     * represents a fresh physical unit, so an already-known {@code serialCode} for this item is a
     * duplicate, not a replay target.
     */
    private SerialNumber resolveReceiveSerial(Item item, UUID serialId, String serialCode, BigDecimal quantity) {
        if (!item.isSerialTracked()) {
            ensureNoSerialProvided(serialId, serialCode);
            return null;
        }
        ensureSerialQuantityIsOne(quantity);
        String normalizedSerialCode = requireSerialCode(serialCode);
        if (serialNumberRepository.findByItemItemIdAndSerialCode(item.getItemId(), normalizedSerialCode).isPresent()) {
            throw ExceptionFactory.alreadyExists(ValidationErrorCode.RESOURCE_ALREADY_EXISTS, "Serial number", normalizedSerialCode);
        }
        return serialNumberRepository.save(SerialNumber.builder()
                .item(item)
                .serialCode(normalizedSerialCode)
                .status(SerialStatus.AVAILABLE)
                .receivedAt(Instant.now())
                .build());
    }

    private SerialNumber resolveExistingSerialForOutbound(Item item, UUID serialId, BigDecimal quantity) {
        SerialNumber serial = resolveExistingSerial(item, serialId, quantity);
        if (serial != null && !serial.canIssue()) {
            throw ExceptionFactory.custom(BusinessErrorCode.SERIAL_NOT_ELIGIBLE,
                    "Serial cannot be issued in status: " + serial.getStatus());
        }
        return serial;
    }

    /** No status check — withdrawing a rejected unit is expected to touch a non-AVAILABLE serial. */
    private SerialNumber resolveExistingSerialForAdjustment(Item item, UUID serialId, BigDecimal quantity) {
        return resolveExistingSerial(item, serialId, quantity);
    }

    private SerialNumber resolveExistingSerial(Item item, UUID serialId, BigDecimal quantity) {
        if (!item.isSerialTracked()) {
            ensureNoSerialProvided(serialId, null);
            return null;
        }
        ensureSerialQuantityIsOne(quantity);
        if (serialId == null) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked item requires a serial id");
        }
        return findSerialForItem(item, serialId);
    }

    private SerialNumber findSerialForItem(Item item, UUID serialId) {
        SerialNumber serial = serialNumberRepository.findById(serialId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Serial number", serialId));
        if (!serial.getItem().getItemId().equals(item.getItemId())) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH,
                    "Serial does not belong to item: " + item.getItemId());
        }
        return serial;
    }

    private void ensureNoSerialProvided(UUID serialId, String serialCode) {
        if (serialId != null || StringUtils.hasText(serialCode)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial can only be provided for serial-tracked items");
        }
    }

    private void ensureSerialQuantityIsOne(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ONE) != 0) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked items must be moved one unit at a time");
        }
    }

    private String requireSerialCode(String serialCode) {
        if (!StringUtils.hasText(serialCode)) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Serial-tracked item requires a serial code");
        }
        return serialCode.trim();
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
        return idempotency.normalizeKey(idempotencyKey);
    }

    /**
     * Looks up a previous movement for this key <b>within the same operation</b> (B5). The type
     * argument is what makes the same key reusable across different operations, and it must match the
     * scope of {@code uk_stock_movements_idempotency_key} (V37) — dropping it here would make the
     * replay return whichever of several same-key rows the database happens to hand back first.
     */
    private Optional<StockMovement> findReplay(String normalizedKey, MovementType movementType) {
        return movementRepository.findByIdempotencyKeyAndMovementType(normalizedKey, movementType);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
