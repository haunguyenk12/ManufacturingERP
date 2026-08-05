package com.erp.manufacturing.module.costing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.costing.domain.ItemStandardCost;
import com.erp.manufacturing.module.costing.repository.ItemStandardCostRepository;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CostingService tests")
class CostingServiceTest {

    @Mock ItemStandardCostRepository itemStandardCostRepository;
    @Mock BomLookupService bomLookupService;

    CostingService service;

    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CostingService(itemStandardCostRepository, bomLookupService);
    }

    private Company company() {
        return Company.builder().companyId(COMPANY_ID).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Item item(UUID itemId, ItemType type) {
        return Item.builder().itemId(itemId).company(company()).code("ITEM").name("Item")
                .type(type).unit("EA").status(ItemStatus.ACTIVE).build();
    }

    private ItemStandardCost cost(BigDecimal material, BigDecimal labor, BigDecimal overhead) {
        return ItemStandardCost.builder()
                .itemStandardCostId(UUID.randomUUID())
                .materialCost(material).laborCost(labor).overheadCost(overhead)
                .build();
    }

    @Test
    @DisplayName("leaf item without a BOM uses its own ItemStandardCost fields directly")
    void calculateStandardCost_leafItem_usesOwnStandardCostFields() {
        UUID itemId = UUID.randomUUID();
        when(bomLookupService.findActiveBom(COMPANY_ID, itemId)).thenReturn(Optional.empty());
        when(itemStandardCostRepository.findByItemItemId(itemId))
                .thenReturn(Optional.of(cost(new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("1"))));

        StandardCostBreakdown breakdown = service.calculateStandardCost(COMPANY_ID, itemId);

        assertThat(breakdown.materialCost()).isEqualByComparingTo("5");
        assertThat(breakdown.laborCost()).isEqualByComparingTo("2");
        assertThat(breakdown.overheadCost()).isEqualByComparingTo("1");
        assertThat(breakdown.totalCost()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("leaf item with no ItemStandardCost row at all defaults every field to ZERO")
    void calculateStandardCost_noStandardCostRow_defaultsToZero() {
        UUID itemId = UUID.randomUUID();
        when(bomLookupService.findActiveBom(COMPANY_ID, itemId)).thenReturn(Optional.empty());
        when(itemStandardCostRepository.findByItemItemId(itemId)).thenReturn(Optional.empty());

        StandardCostBreakdown breakdown = service.calculateStandardCost(COMPANY_ID, itemId);

        assertThat(breakdown.totalCost()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("manufactured item with an ACTIVE BOM: material cost rolls up from components, " +
            "labor/overhead come from its own flat rate — never from the BOM")
    void calculateStandardCost_manufacturedItem_rollsUpMaterialFromComponents() {
        UUID parentId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        Item parent = item(parentId, ItemType.FINISHED_GOOD);
        Item component = item(componentId, ItemType.RAW_MATERIAL);

        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID()).company(company()).parentItem(parent)
                .revision("R1").status(BomStatus.ACTIVE).lines(new ArrayList<>())
                .build();
        bom.getLines().add(BomLine.builder()
                .lineId(UUID.randomUUID()).componentItem(component).lineNo(10)
                .quantityPer(new BigDecimal("2")).scrapRate(new BigDecimal("0.10"))
                .build());

        when(bomLookupService.findActiveBom(COMPANY_ID, parentId)).thenReturn(Optional.of(bom));
        when(bomLookupService.findActiveBom(COMPANY_ID, componentId)).thenReturn(Optional.empty());
        when(itemStandardCostRepository.findByItemItemId(parentId))
                .thenReturn(Optional.of(cost(BigDecimal.ZERO, new BigDecimal("3"), new BigDecimal("1"))));
        when(itemStandardCostRepository.findByItemItemId(componentId))
                .thenReturn(Optional.of(cost(new BigDecimal("5"), BigDecimal.ZERO, BigDecimal.ZERO)));

        StandardCostBreakdown breakdown = service.calculateStandardCost(COMPANY_ID, parentId);

        // materialCost = componentTotalCost (5) * quantityPer (2) * (1 + scrapRate 0.10) = 11.0000
        assertThat(breakdown.materialCost()).isEqualByComparingTo("11.0000");
        assertThat(breakdown.laborCost()).isEqualByComparingTo("3");
        assertThat(breakdown.overheadCost()).isEqualByComparingTo("1");
        assertThat(breakdown.totalCost()).isEqualByComparingTo("15.0000");
    }

    @Test
    @DisplayName("manufactured item's own materialCost field is ignored when an ACTIVE BOM exists")
    void calculateStandardCost_manufacturedItem_ignoresOwnMaterialCostField() {
        UUID parentId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        Item parent = item(parentId, ItemType.FINISHED_GOOD);
        Item component = item(componentId, ItemType.RAW_MATERIAL);

        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID()).company(company()).parentItem(parent)
                .revision("R1").status(BomStatus.ACTIVE).lines(new ArrayList<>())
                .build();
        bom.getLines().add(BomLine.builder()
                .lineId(UUID.randomUUID()).componentItem(component).lineNo(10)
                .quantityPer(BigDecimal.ONE).scrapRate(BigDecimal.ZERO)
                .build());

        when(bomLookupService.findActiveBom(COMPANY_ID, parentId)).thenReturn(Optional.of(bom));
        when(bomLookupService.findActiveBom(COMPANY_ID, componentId)).thenReturn(Optional.empty());
        // Parent's own materialCost field is set (e.g. stale data) but must NOT be used.
        when(itemStandardCostRepository.findByItemItemId(parentId))
                .thenReturn(Optional.of(cost(new BigDecimal("999"), BigDecimal.ZERO, BigDecimal.ZERO)));
        when(itemStandardCostRepository.findByItemItemId(componentId))
                .thenReturn(Optional.of(cost(new BigDecimal("7"), BigDecimal.ZERO, BigDecimal.ZERO)));

        StandardCostBreakdown breakdown = service.calculateStandardCost(COMPANY_ID, parentId);

        assertThat(breakdown.materialCost()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("a component repeated at a deeper level that cycles back to an ancestor throws " +
            "BOM_CIRCULAR_REFERENCE (defense-in-depth — B8 should already prevent this)")
    void calculateStandardCost_circularBom_throwsBomCircularReference() {
        UUID itemAId = UUID.randomUUID();
        UUID itemBId = UUID.randomUUID();
        Item itemA = item(itemAId, ItemType.FINISHED_GOOD);
        Item itemB = item(itemBId, ItemType.WIP);

        BomHeader bomA = BomHeader.builder()
                .bomId(UUID.randomUUID()).company(company()).parentItem(itemA)
                .revision("R1").status(BomStatus.ACTIVE).lines(new ArrayList<>())
                .build();
        bomA.getLines().add(BomLine.builder()
                .lineId(UUID.randomUUID()).componentItem(itemB).lineNo(10)
                .quantityPer(BigDecimal.ONE).scrapRate(BigDecimal.ZERO).build());

        BomHeader bomB = BomHeader.builder()
                .bomId(UUID.randomUUID()).company(company()).parentItem(itemB)
                .revision("R1").status(BomStatus.ACTIVE).lines(new ArrayList<>())
                .build();
        bomB.getLines().add(BomLine.builder()
                .lineId(UUID.randomUUID()).componentItem(itemA).lineNo(10)
                .quantityPer(BigDecimal.ONE).scrapRate(BigDecimal.ZERO).build());

        when(bomLookupService.findActiveBom(COMPANY_ID, itemAId)).thenReturn(Optional.of(bomA));
        when(bomLookupService.findActiveBom(COMPANY_ID, itemBId)).thenReturn(Optional.of(bomB));

        assertThatThrownBy(() -> service.calculateStandardCost(COMPANY_ID, itemAId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BOM_CIRCULAR_REFERENCE));
    }
}
