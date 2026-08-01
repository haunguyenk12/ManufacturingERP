package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingLookupService tests")
class RoutingLookupServiceTest {

    @Mock RoutingHeaderRepository routingHeaderRepository;

    RoutingLookupService service;

    @BeforeEach
    void setUp() {
        service = new RoutingLookupService(routingHeaderRepository);
    }

    @Test
    void getActiveRouting_itemWithoutActiveRouting_throwsMissingRouting() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(routingHeaderRepository.findWithOperationsByCompanyCompanyIdAndItemItemIdAndStatus(
                companyId, itemId, RoutingStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveRouting(companyId, itemId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.MISSING_ROUTING));
    }

    @Test
    void findActiveRouting_itemWithoutActiveRouting_returnsEmptyInsteadOfThrowing() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(routingHeaderRepository.findWithOperationsByCompanyCompanyIdAndItemItemIdAndStatus(
                companyId, itemId, RoutingStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.findActiveRouting(companyId, itemId)).isEmpty();
    }

    @Test
    void getActiveRouting_activeRoutingExists_returnsIt() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        RoutingHeader routing = RoutingHeader.builder()
                .routingId(UUID.randomUUID())
                .code("RT-FG100")
                .routingVersion("V1")
                .status(RoutingStatus.ACTIVE)
                .build();
        when(routingHeaderRepository.findWithOperationsByCompanyCompanyIdAndItemItemIdAndStatus(
                companyId, itemId, RoutingStatus.ACTIVE)).thenReturn(Optional.of(routing));

        assertThat(service.getActiveRouting(companyId, itemId).getCode()).isEqualTo("RT-FG100");
    }

    /**
     * F10 / debt F. The summary carries the routing's own {@code code} and its business
     * {@code routingVersion} — not the item SKU and not {@code BaseEntity.version}, the two fields
     * sitting next to them that a mapping slip would silently pick up instead (B78).
     */
    @Test
    void findActiveRoutingSummaries_carriesTheRoutingCodeAndBusinessVersionKeyedByItem() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID routingId = UUID.randomUUID();
        RoutingHeader routing = RoutingHeader.builder()
                .routingId(routingId)
                .item(Item.builder().itemId(itemId).code("FG-100").build())
                .code("RT-FG100")
                .routingVersion("V1")
                .status(RoutingStatus.ACTIVE)
                .build();
        when(routingHeaderRepository.findByCompanyCompanyIdAndItemItemIdInAndStatus(
                companyId, List.of(itemId), RoutingStatus.ACTIVE)).thenReturn(List.of(routing));

        Map<UUID, RoutingLookupService.RoutingSummary> summaries =
                service.findActiveRoutingSummaries(companyId, List.of(itemId));

        assertThat(summaries).containsOnlyKeys(itemId);
        assertThat(summaries.get(itemId).routingId()).isEqualTo(routingId);
        assertThat(summaries.get(itemId).code()).isEqualTo("RT-FG100");
        assertThat(summaries.get(itemId).version()).isEqualTo("V1");
    }

    /** No item ids means no query at all — MRP calls this once per BOM level, empty levels included. */
    @Test
    void findActiveRoutingSummaries_withoutItemIds_returnsEmptyWithoutQuerying() {
        assertThat(service.findActiveRoutingSummaries(UUID.randomUUID(), List.of())).isEmpty();

        verifyNoInteractions(routingHeaderRepository);
    }
}
