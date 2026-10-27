package com.erp.manufacturing.module.delivery.service;

import com.erp.manufacturing.common.audit.SecurityAuditorAware;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.module.delivery.dto.DeliveryResponse;
import com.erp.manufacturing.module.delivery.repository.DeliveryLineRepository;
import com.erp.manufacturing.module.delivery.repository.DeliveryRepository;
import com.erp.manufacturing.module.inventory.service.InventoryLotLookupService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.organization.security.PermissionGuard;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.sales.service.SalesOrderCommercialLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.service.execution.SalesOrderStockReservationService;
import com.erp.manufacturing.module.workorder.service.query.ProductionOutputLotLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** G10d (FE Q38): "related deliveries" takes exactly one source; a receipt or work order stands for the lots it produced. */
class DeliveryRelatedTest {

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final ProductionOutputLotLookupService outputLots = mock(ProductionOutputLotLookupService.class);
    private final DeliveryQueryService service = new DeliveryQueryService(deliveryRepository, mock(DeliveryLineRepository.class),
            mock(DeliverySupport.class), mock(SalesOrderCommercialLookupService.class),
            mock(SalesOrderStockReservationService.class), mock(InventoryMovementService.class),
            mock(InventoryLotLookupService.class), mock(OrganizationLookupService.class), mock(UserLookupService.class),
            mock(PermissionGuard.class), mock(SecurityAuditorAware.class), outputLots);

    private final UUID plantId = UUID.randomUUID();

    @Test
    void noneOrTwoSources_isA400BeforeAnyQuery() {
        for (UUID[] filters : List.of(new UUID[]{null, null, null}, new UUID[]{UUID.randomUUID(), UUID.randomUUID(), null})) {
            assertThatThrownBy(() -> service.related(plantId, filters[0], filters[1], filters[2], PageRequest.of(0, 20)))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT));
        }
        verifyNoInteractions(deliveryRepository, outputLots);
    }

    @Test
    void aReceiptStandsForTheLotsItProduced_andOneThatProducedNoneHasNoDeliveries() {
        UUID receiptId = UUID.randomUUID();
        UUID lot = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        when(outputLots.findOutputLotIds(receiptId, null)).thenReturn(Set.of(lot, child));
        Page<com.erp.manufacturing.module.delivery.domain.Delivery> empty = new PageImpl<>(List.of());
        when(deliveryRepository.findPostedFromLots(eq(plantId), any(), any())).thenReturn(empty);

        service.related(plantId, null, receiptId, null, PageRequest.of(0, 20));
        verify(deliveryRepository).findPostedFromLots(eq(plantId), eq(Set.of(lot, child)), any());

        UUID workOrderId = UUID.randomUUID();
        when(outputLots.findOutputLotIds(null, workOrderId)).thenReturn(Set.of());
        PageResult<DeliveryResponse> none = service.related(plantId, null, null, workOrderId, PageRequest.of(0, 20));
        assertThat(none.content()).isEmpty();
        verify(deliveryRepository, times(1)).findPostedFromLots(any(), any(), any());
    }
}
