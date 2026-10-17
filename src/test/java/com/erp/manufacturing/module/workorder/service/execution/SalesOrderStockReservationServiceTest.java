package com.erp.manufacturing.module.workorder.service.execution;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.inventory.domain.InventoryLot;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.LotStatus;
import com.erp.manufacturing.module.inventory.domain.StockBalance;
import com.erp.manufacturing.module.inventory.repository.StockBalanceRepository;
import com.erp.manufacturing.module.organization.domain.Warehouse;
import com.erp.manufacturing.module.workorder.domain.MaterialReservation;
import com.erp.manufacturing.module.workorder.domain.MaterialReservationStatus;
import com.erp.manufacturing.module.workorder.repository.MaterialReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderStockReservationServiceTest {

    @Mock MaterialReservationRepository reservationRepository;
    @Mock StockBalanceRepository stockBalanceRepository;

    SalesOrderStockReservationService service;

    private final UUID salesOrderId = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        service = new SalesOrderStockReservationService(reservationRepository, stockBalanceRepository);
        warehouse = Warehouse.builder().warehouseId(UUID.randomUUID()).code("FG").build();
    }

    @Test
    void reserve_namedLot_movesTheQuantityToReservedAndOwnsTheRowBySalesOrder() {
        Item item = item(true);
        StockBalance balance = balance(item, lot(LotStatus.AVAILABLE, null), "10");
        when(stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(
                item.getItemId(), warehouse.getWarehouseId(), balance.getLot().getLotId()))
                .thenReturn(Optional.of(balance));
        when(reservationRepository.save(any(MaterialReservation.class))).thenAnswer(i -> i.getArgument(0));

        List<MaterialReservation> created = service.reserve(salesOrderId, lineId, item, warehouse,
                balance.getLot().getLotId(), new BigDecimal("4"));

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4");
        assertThat(created).singleElement().satisfies(r -> {
            assertThat(r.getSalesOrderId()).isEqualTo(salesOrderId);
            assertThat(r.getSalesOrderLineId()).isEqualTo(lineId);
            assertThat(r.getWorkOrder()).isNull();
            assertThat(r.getComponentLine()).isNull();
            assertThat(r.getLot()).isSameAs(balance.getLot());
            assertThat(r.getStatus()).isEqualTo(MaterialReservationStatus.ACTIVE);
        });
        verify(stockBalanceRepository).save(balance);
    }

    @Test
    void reserve_lotOnHold_isRefusedBeforeAnyWrite() {
        Item item = item(true);
        StockBalance balance = balance(item, lot(LotStatus.HOLD, null), "10");
        when(stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(any(), any(), any()))
                .thenReturn(Optional.of(balance));

        assertCode(() -> service.reserve(salesOrderId, lineId, item, warehouse, balance.getLot().getLotId(),
                BigDecimal.ONE), BusinessErrorCode.LOT_NOT_ELIGIBLE);

        verify(stockBalanceRepository, never()).save(any());
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void reserve_expiredLot_isRefusedBeforeAnyWrite() {
        Item item = item(true);
        StockBalance balance = balance(item,
                lot(LotStatus.AVAILABLE, Instant.now().minus(1, ChronoUnit.HOURS)), "10");
        when(stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotLotId(any(), any(), any()))
                .thenReturn(Optional.of(balance));

        assertCode(() -> service.reserve(salesOrderId, lineId, item, warehouse, balance.getLot().getLotId(),
                BigDecimal.ONE), BusinessErrorCode.LOT_NOT_ELIGIBLE);

        verifyNoInteractions(reservationRepository);
    }

    @Test
    void reserve_moreThanFreeStock_isRefusedBeforeAnyWrite() {
        Item item = item(false);
        StockBalance balance = balance(item, null, "10");
        balance.setReservedQuantity(new BigDecimal("8"));
        when(stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(
                item.getItemId(), warehouse.getWarehouseId())).thenReturn(Optional.of(balance));

        assertCode(() -> service.reserve(salesOrderId, lineId, item, warehouse, null, new BigDecimal("3")),
                BusinessErrorCode.INSUFFICIENT_STOCK);

        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("8");
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void reserve_lotTrackedWithoutALot_spreadsFefoAndWritesOneRowPerLot() {
        Item item = item(true);
        StockBalance first = balance(item, lot(LotStatus.AVAILABLE, null), "3");
        StockBalance second = balance(item, lot(LotStatus.AVAILABLE, null), "10");
        when(stockBalanceRepository.findReservableBalancesFefo(eq(item.getItemId()), anyCollection(),
                eq(LotStatus.AVAILABLE), any(Instant.class), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(reservationRepository.save(any(MaterialReservation.class))).thenAnswer(i -> i.getArgument(0));

        List<MaterialReservation> created = service.reserve(salesOrderId, lineId, item, warehouse, null,
                new BigDecimal("5"));

        assertThat(created).extracting(MaterialReservation::getQuantity)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("3"), new BigDecimal("2"));
        assertThat(first.getReservedQuantity()).isEqualByComparingTo("3");
        assertThat(second.getReservedQuantity()).isEqualByComparingTo("2");
    }

    /** All or nothing: a shortfall across every eligible lot holds nothing at all. */
    @Test
    void reserve_lotTrackedWithoutALot_shortfall_holdsNothing() {
        Item item = item(true);
        StockBalance only = balance(item, lot(LotStatus.AVAILABLE, null), "3");
        when(stockBalanceRepository.findReservableBalancesFefo(any(), anyCollection(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(only)));

        assertCode(() -> service.reserve(salesOrderId, lineId, item, warehouse, null, new BigDecimal("5")),
                BusinessErrorCode.INSUFFICIENT_STOCK);

        assertThat(only.getReservedQuantity()).isZero();
        verify(stockBalanceRepository, never()).save(any());
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void reserve_serialTrackedItem_isRefused() {
        Item item = item(false);
        item.setSerialTracked(true);

        assertCode(() -> service.reserve(salesOrderId, lineId, item, warehouse, null, BigDecimal.ONE),
                BusinessErrorCode.OPERATION_NOT_ALLOWED);

        verifyNoInteractions(stockBalanceRepository, reservationRepository);
    }

    /** FE A09: another order's (or a work order's) hold is never reachable through this order. */
    @Test
    void release_holdOfAnotherOrder_isNotFound() {
        MaterialReservation other = MaterialReservation.builder().reservationId(UUID.randomUUID())
                .salesOrderId(UUID.randomUUID()).status(MaterialReservationStatus.ACTIVE).build();
        when(reservationRepository.findWithDetailsByReservationId(other.getReservationId()))
                .thenReturn(Optional.of(other));

        assertCode(() -> service.release(salesOrderId, other.getReservationId()),
                ValidationErrorCode.RESOURCE_NOT_FOUND);

        verifyNoInteractions(stockBalanceRepository);
    }

    @Test
    void release_ownActiveHold_returnsTheRemainderToFreeStock() {
        Item item = item(false);
        StockBalance balance = balance(item, null, "10");
        balance.setReservedQuantity(new BigDecimal("6"));
        MaterialReservation own = MaterialReservation.builder().reservationId(UUID.randomUUID())
                .salesOrderId(salesOrderId).salesOrderLineId(lineId).item(item).warehouse(warehouse)
                .quantity(new BigDecimal("6")).consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE).build();
        when(reservationRepository.findWithDetailsByReservationId(own.getReservationId())).thenReturn(Optional.of(own));
        when(stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(any(), any()))
                .thenReturn(Optional.of(balance));
        when(reservationRepository.save(own)).thenReturn(own);

        MaterialReservation released = service.release(salesOrderId, own.getReservationId());

        assertThat(released.getStatus()).isEqualTo(MaterialReservationStatus.RELEASED);
        assertThat(balance.getReservedQuantity()).isZero();
    }

    @Test
    void cancelActiveForSalesOrder_releasesEveryActiveHoldAsCancelled() {
        Item item = item(false);
        StockBalance balance = balance(item, null, "10");
        balance.setReservedQuantity(new BigDecimal("5"));
        MaterialReservation a = hold(item, "2");
        MaterialReservation b = hold(item, "3");
        when(reservationRepository.findBySalesOrderIdAndStatus(salesOrderId, MaterialReservationStatus.ACTIVE))
                .thenReturn(List.of(a, b));
        when(stockBalanceRepository.findByItemItemIdAndWarehouseWarehouseIdAndLotIsNull(any(), any()))
                .thenReturn(Optional.of(balance));

        assertThat(service.cancelActiveForSalesOrder(salesOrderId)).isEqualTo(2);

        assertThat(balance.getReservedQuantity()).isZero();
        assertThat(List.of(a, b)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(MaterialReservationStatus.CANCELLED));
        ArgumentCaptor<MaterialReservation> saved = ArgumentCaptor.forClass(MaterialReservation.class);
        verify(reservationRepository, times(2)).save(saved.capture());
    }

    private MaterialReservation hold(Item item, String quantity) {
        return MaterialReservation.builder().reservationId(UUID.randomUUID())
                .salesOrderId(salesOrderId).salesOrderLineId(lineId).item(item).warehouse(warehouse)
                .quantity(new BigDecimal(quantity)).consumedQuantity(BigDecimal.ZERO)
                .status(MaterialReservationStatus.ACTIVE).build();
    }

    private Item item(boolean lotTracked) {
        return Item.builder().itemId(UUID.randomUUID()).code("FG").unit("EA").lotTracked(lotTracked).build();
    }

    private InventoryLot lot(LotStatus status, Instant expiresAt) {
        return InventoryLot.builder().lotId(UUID.randomUUID()).lotCode("L-" + UUID.randomUUID())
                .status(status).expiresAt(expiresAt).build();
    }

    private StockBalance balance(Item item, InventoryLot lot, String quantity) {
        return StockBalance.builder().balanceId(UUID.randomUUID()).item(item).warehouse(warehouse).lot(lot)
                .quantity(new BigDecimal(quantity)).build();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(code));
    }
}
