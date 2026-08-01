package com.erp.manufacturing.module.purchasing.service.query;

import com.erp.manufacturing.module.purchasing.domain.PurchaseOrderStatus;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderSupplyProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D4.1 / debt #16.
 *
 * <p>The status set is the business decision that lives in Java, so it is asserted here by capturing
 * what is handed to the repository — a mutation of {@code OPEN_SUPPLY_STATUSES} turns this test red.
 * The remaining filters (open quantity {@code > 0}, warehouse and plant scope) live in JPQL and are
 * covered for real in {@code PurchaseOrderRepositoryIT}, not faked with a mock here (rule {@code R7}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderSupplyService tests")
class PurchaseOrderSupplyServiceTest {

    @Mock PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks PurchaseOrderSupplyService service;

    @Test
    void getOpenSupplyQuantities_onlyCountsOrdersAlreadySentToTheSupplier() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(purchaseOrderRepository.aggregateOpenSupply(any(), any(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(List.of(projection(itemId, "12.5")));

        Map<UUID, BigDecimal> openSupply = service.getOpenSupplyQuantities(
                companyId, plantId, List.of(warehouseId), List.of(itemId));

        assertThat(openSupply).containsExactly(Map.entry(itemId, new BigDecimal("12.5")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PurchaseOrderStatus>> statuses =
                ArgumentCaptor.forClass(Collection.class);
        verify(purchaseOrderRepository).aggregateOpenSupply(
                any(), any(), anyCollection(), anyCollection(), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED);
        // A DRAFT order is not a promise from anyone; RECEIVED/CANCELLED have nothing left to arrive.
        assertThat(statuses.getValue()).doesNotContain(
                PurchaseOrderStatus.DRAFT,
                PurchaseOrderStatus.RECEIVED,
                PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void getOpenSupplyQuantities_emptyItemsOrWarehouses_returnsEmptyWithoutTouchingTheRepository() {
        UUID companyId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        assertThat(service.getOpenSupplyQuantities(companyId, plantId, List.of(), List.of(id))).isEmpty();
        assertThat(service.getOpenSupplyQuantities(companyId, plantId, List.of(id), List.of())).isEmpty();
        assertThat(service.getOpenSupplyQuantities(null, plantId, List.of(id), List.of(id))).isEmpty();
        assertThat(service.getOpenSupplyQuantities(companyId, null, List.of(id), List.of(id))).isEmpty();

        verifyNoInteractions(purchaseOrderRepository);
    }

    private PurchaseOrderSupplyProjection projection(UUID itemId, String openSupplyQuantity) {
        return new PurchaseOrderSupplyProjection() {
            @Override
            public UUID getItemId() {
                return itemId;
            }

            @Override
            public BigDecimal getOpenSupplyQuantity() {
                return new BigDecimal(openSupplyQuantity);
            }
        };
    }
}
