package com.erp.manufacturing.module.costing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.costing.domain.ItemStandardCost;
import com.erp.manufacturing.module.costing.dto.ItemStandardCostRequest;
import com.erp.manufacturing.module.costing.mapper.CostingMapper;
import com.erp.manufacturing.module.costing.repository.ItemStandardCostRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItemStandardCostService tests")
class ItemStandardCostServiceTest {

    @Mock ItemStandardCostRepository repository;
    @Mock ItemLookupService itemLookupService;
    @Mock CostingService costingService;

    ItemStandardCostService service;

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ItemStandardCostService(repository, itemLookupService, costingService, new CostingMapper());
        lenient().when(costingService.calculateStandardCost(eq(COMPANY_ID), any()))
                .thenReturn(new StandardCostBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private Company company(UUID companyId) {
        return Company.builder().companyId(companyId).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Item activeItem(UUID itemId, UUID companyId) {
        return Item.builder().itemId(itemId).company(company(companyId)).code("RM-001").name("Raw Material")
                .type(ItemType.RAW_MATERIAL).unit("EA").status(ItemStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("upsert: no existing row creates a new ItemStandardCost")
    void upsert_noExistingRow_createsNew() {
        when(itemLookupService.getActiveItem(ITEM_ID)).thenReturn(activeItem(ITEM_ID, COMPANY_ID));
        when(repository.findByItemItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(repository.save(any(ItemStandardCost.class))).thenAnswer(invocation -> {
            ItemStandardCost cost = invocation.getArgument(0);
            cost.setItemStandardCostId(UUID.randomUUID());
            return cost;
        });

        var response = service.upsert(COMPANY_ID, ITEM_ID,
                new ItemStandardCostRequest(new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("1")));

        assertThat(response.materialCost()).isEqualByComparingTo("5");
        assertThat(response.laborCost()).isEqualByComparingTo("2");
        assertThat(response.overheadCost()).isEqualByComparingTo("1");
        assertThat(response.itemId()).isEqualTo(ITEM_ID);
        assertThat(response.companyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    @DisplayName("upsert: existing row is updated in place, not duplicated")
    void upsert_existingRow_updatesInPlace() {
        UUID existingId = UUID.randomUUID();
        ItemStandardCost existing = ItemStandardCost.builder()
                .itemStandardCostId(existingId).company(company(COMPANY_ID)).item(activeItem(ITEM_ID, COMPANY_ID))
                .materialCost(new BigDecimal("1")).laborCost(new BigDecimal("1")).overheadCost(new BigDecimal("1"))
                .build();
        when(itemLookupService.getActiveItem(ITEM_ID)).thenReturn(activeItem(ITEM_ID, COMPANY_ID));
        when(repository.findByItemItemId(ITEM_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(ItemStandardCost.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upsert(COMPANY_ID, ITEM_ID,
                new ItemStandardCostRequest(new BigDecimal("9"), new BigDecimal("8"), new BigDecimal("7")));

        assertThat(response.itemStandardCostId()).isEqualTo(existingId);
        assertThat(response.materialCost()).isEqualByComparingTo("9");
        assertThat(response.laborCost()).isEqualByComparingTo("8");
        assertThat(response.overheadCost()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("upsert: item belonging to a different company throws RESOURCE_SCOPE_MISMATCH before any save")
    void upsert_itemOfDifferentCompany_throwsBeforeSaving() {
        UUID otherCompanyId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(ITEM_ID)).thenReturn(activeItem(ITEM_ID, otherCompanyId));

        assertThatThrownBy(() -> service.upsert(COMPANY_ID, ITEM_ID,
                new ItemStandardCostRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("upsert: inactive item propagates the rejection from ItemLookupService")
    void upsert_inactiveItem_propagatesRejection() {
        when(itemLookupService.getActiveItem(ITEM_ID)).thenThrow(
                new AppException(BusinessErrorCode.RESOURCE_INACTIVE, "Inactive item cannot be used: " + ITEM_ID));

        assertThatThrownBy(() -> service.upsert(COMPANY_ID, ITEM_ID,
                new ItemStandardCostRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("get: unknown item standard cost throws RESOURCE_NOT_FOUND")
    void get_unknownCost_throwsResourceNotFound() {
        when(itemLookupService.getActiveItem(ITEM_ID)).thenReturn(activeItem(ITEM_ID, COMPANY_ID));
        when(repository.findByItemItemId(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(COMPANY_ID, ITEM_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("list: passes the companyId and itemId filter straight through to the repository")
    void list_passesFiltersThrough() {
        when(repository.search(eq(COMPANY_ID), eq(ITEM_ID), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(COMPANY_ID, ITEM_ID, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(repository).search(eq(COMPANY_ID), eq(ITEM_ID), any());
    }
}
