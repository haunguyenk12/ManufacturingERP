package com.erp.manufacturing.module.planning.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.ScopeResourceType;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.organization.service.OrganizationScopeResolution;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateRequest;
import com.erp.manufacturing.module.planning.dto.ProductionEstimateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanningService tests")
class PlanningServiceTest {

    @Mock ItemLookupService itemLookupService;
    @Mock BomLookupService bomLookupService;
    @Mock InventoryAvailabilityService inventoryAvailabilityService;
    @Mock OrganizationLookupService organizationLookupService;

    PlanningService service;

    @BeforeEach
    void setUp() {
        service = new PlanningService(
                itemLookupService,
                bomLookupService,
                inventoryAvailabilityService,
                organizationLookupService);
    }

    @Test
    void estimateProduction_singleLevelBom_calculatesScrapShortageAndMaxBuildable() {
        UUID companyId = UUID.randomUUID();
        UUID scopeId = companyId;
        UUID warehouseId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        Item component = item(UUID.randomUUID(), companyId, "RM-001", ItemType.RAW_MATERIAL);
        BomHeader rootBom = bom(product, line(component, "2.0", "0.10"));
        ProductionEstimateRequest request = request(product.getItemId(), ScopeResourceType.COMPANY, scopeId, "10");

        when(organizationLookupService.resolveScope(ScopeResourceType.COMPANY, scopeId))
                .thenReturn(scope(ScopeResourceType.COMPANY, scopeId, companyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        when(bomLookupService.getActiveBom(companyId, product.getItemId())).thenReturn(rootBom);
        when(inventoryAvailabilityService.getAvailableQuantities(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(component.getItemId(), bd("15")));

        ProductionEstimateResponse response = service.estimateProduction(request);

        assertThat(response.maxBuildableQuantity()).isEqualByComparingTo("6.818181");
        assertThat(response.summary().feasible()).isFalse();
        assertThat(response.summary().shortageLineCount()).isEqualTo(1);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).requiredQuantity()).isEqualByComparingTo("22.00");
        assertThat(response.lines().get(0).availableQuantity()).isEqualByComparingTo("15");
        assertThat(response.lines().get(0).shortageQuantity()).isEqualByComparingTo("7.00");
    }

    @Test
    void estimateProduction_multiLevelBom_aggregatesRepeatedLeafComponent() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        Item subAssembly = item(UUID.randomUUID(), companyId, "SUB-100", ItemType.WIP);
        Item repeatedComponent = item(UUID.randomUUID(), companyId, "RM-001", ItemType.RAW_MATERIAL);
        BomHeader rootBom = bom(product,
                line(subAssembly, "2", "0"),
                line(repeatedComponent, "1", "0"));
        BomHeader childBom = bom(subAssembly, line(repeatedComponent, "3", "0.10"));
        ProductionEstimateRequest request = request(product.getItemId(), ScopeResourceType.WAREHOUSE, warehouseId, "10");

        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        when(bomLookupService.getActiveBom(companyId, product.getItemId())).thenReturn(rootBom);
        when(bomLookupService.findActiveBom(companyId, subAssembly.getItemId())).thenReturn(Optional.of(childBom));
        when(inventoryAvailabilityService.getAvailableQuantities(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(repeatedComponent.getItemId(), bd("100")));

        ProductionEstimateResponse response = service.estimateProduction(request);

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).requiredQuantity()).isEqualByComparingTo("76.0");
        assertThat(response.lines().get(0).shortageQuantity()).isEqualByComparingTo("0");
        assertThat(response.maxBuildableQuantity()).isEqualByComparingTo("13.157894");
    }

    @Test
    void estimateProduction_wipWithoutActiveBom_becomesLeafRequirement() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        Item subAssembly = item(UUID.randomUUID(), companyId, "SUB-100", ItemType.WIP);
        BomHeader rootBom = bom(product, line(subAssembly, "2", "0"));
        ProductionEstimateRequest request = request(product.getItemId(), ScopeResourceType.WAREHOUSE, warehouseId, "10");

        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        when(bomLookupService.getActiveBom(companyId, product.getItemId())).thenReturn(rootBom);
        when(bomLookupService.findActiveBom(companyId, subAssembly.getItemId())).thenReturn(Optional.empty());
        when(inventoryAvailabilityService.getAvailableQuantities(anyCollection(), eq(List.of(warehouseId))))
                .thenReturn(Map.of(subAssembly.getItemId(), bd("20")));

        ProductionEstimateResponse response = service.estimateProduction(request);

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).componentItemId()).isEqualTo(subAssembly.getItemId());
        assertThat(response.lines().get(0).requiredQuantity()).isEqualByComparingTo("20");
        assertThat(response.summary().feasible()).isTrue();
    }

    @Test
    void estimateProduction_circularActiveBom_failsDefensively() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        Item subAssembly = item(UUID.randomUUID(), companyId, "SUB-100", ItemType.WIP);
        BomHeader rootBom = bom(product, line(subAssembly, "1", "0"));
        BomHeader childBom = bom(subAssembly, line(product, "1", "0"));
        ProductionEstimateRequest request = request(product.getItemId(), ScopeResourceType.WAREHOUSE, warehouseId, "1");

        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        when(bomLookupService.getActiveBom(companyId, product.getItemId())).thenReturn(rootBom);
        when(bomLookupService.findActiveBom(companyId, subAssembly.getItemId())).thenReturn(Optional.of(childBom));

        assertThatThrownBy(() -> service.estimateProduction(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BOM_CIRCULAR_REFERENCE));

        verifyNoInteractions(inventoryAvailabilityService);
    }

    @Test
    void estimateProduction_productScopeCompanyMismatch_fails() {
        UUID scopeCompanyId = UUID.randomUUID();
        UUID productCompanyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), productCompanyId, "FG-100", ItemType.FINISHED_GOOD);
        ProductionEstimateRequest request = request(product.getItemId(), ScopeResourceType.WAREHOUSE, warehouseId, "1");

        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, scopeCompanyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);

        assertThatThrownBy(() -> service.estimateProduction(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verifyNoInteractions(bomLookupService, inventoryAvailabilityService);
    }

    @Test
    void estimateProduction_missingRootActiveBom_returnsNotFound() {
        UUID companyId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Item product = item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD);
        ProductionEstimateRequest request = request(product.getItemId(), ScopeResourceType.WAREHOUSE, warehouseId, "1");

        when(organizationLookupService.resolveScope(ScopeResourceType.WAREHOUSE, warehouseId))
                .thenReturn(scope(ScopeResourceType.WAREHOUSE, warehouseId, companyId, List.of(warehouseId)));
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        when(bomLookupService.getActiveBom(companyId, product.getItemId()))
                .thenThrow(ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "Active BOM", product.getItemId()));

        assertThatThrownBy(() -> service.estimateProduction(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    private ProductionEstimateRequest request(UUID productItemId,
                                              ScopeResourceType scopeType,
                                              UUID scopeId,
                                              String targetQuantity) {
        return new ProductionEstimateRequest(productItemId, scopeType, scopeId, bd(targetQuantity));
    }

    private OrganizationScopeResolution scope(ScopeResourceType scopeType,
                                              UUID scopeId,
                                              UUID companyId,
                                              List<UUID> warehouseIds) {
        return new OrganizationScopeResolution(scopeType, scopeId, companyId, warehouseIds);
    }

    private BomHeader bom(Item parent, BomLine... lines) {
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(parent.getCompany())
                .parentItem(parent)
                .revision("R1")
                .status(BomStatus.ACTIVE)
                .lines(new java.util.ArrayList<>())
                .build();
        for (BomLine line : lines) {
            line.setBom(bom);
            bom.getLines().add(line);
        }
        return bom;
    }

    private BomLine line(Item component, String quantityPer, String scrapRate) {
        return BomLine.builder()
                .lineId(UUID.randomUUID())
                .componentItem(component)
                .lineNo(10)
                .quantityPer(bd(quantityPer))
                .scrapRate(bd(scrapRate))
                .build();
    }

    private Item item(UUID itemId, UUID companyId, String code, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(Company.builder()
                        .companyId(companyId)
                        .code("ACME")
                        .name("ACME")
                        .status(OrganizationStatus.ACTIVE)
                        .build())
                .code(code)
                .name(code)
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
