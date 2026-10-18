package com.erp.manufacturing.module.planning.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.audit.BaseEntity;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.planning.domain.*;
import com.erp.manufacturing.module.planning.service.PlanningDemandService;
import com.erp.manufacturing.module.planning.service.PlanningSourceVersionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G6b, rule R7: the queries the commercial run and the conversion ledger rely on, and the V75
 * constraints, against real Postgres — a mocked repository cannot show any of them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlanningConversionRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired TestEntityManager entityManager;
    @Autowired MrpRunRepository mrpRunRepository;
    @Autowired MrpRunDemandRepository mrpRunDemandRepository;
    @Autowired MrpRequirementLineRepository requirementLineRepository;
    @Autowired SupplySuggestionRepository supplySuggestionRepository;
    @Autowired SupplySuggestionConversionRepository conversionRepository;

    Company company;
    Plant plant;
    Warehouse warehouse;
    Item product;
    Item raw;
    MrpRun run;
    MrpRequirementLine rootLine;
    MrpRequirementLine componentLine;
    SupplySuggestion makeSuggestion;
    UUID salesOrderLineId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        company = persist(Company.builder().code("CO_" + suffix).name("Co").status(OrganizationStatus.ACTIVE).build());
        plant = persist(Plant.builder().company(company).code("PL_" + suffix).name("Plant").status(OrganizationStatus.ACTIVE).build());
        warehouse = persist(Warehouse.builder().plant(plant).code("WH_" + suffix).name("Wh")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        product = persist(Item.builder().company(company).code("FG_" + suffix).name("FG")
                .type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build());
        raw = persist(Item.builder().company(company).code("RM_" + suffix).name("RM")
                .type(ItemType.RAW_MATERIAL).unit("KG").status(ItemStatus.ACTIVE).build());
        salesOrderLineId = UUID.randomUUID();
        PlanningDemand demand = persist(PlanningDemand.builder()
                .company(company).plant(plant).item(product)
                .demandType(PlanningDemandType.SALES_ORDER)
                .requiredQuantity(new BigDecimal("100"))
                .dueDate(LocalDate.of(2026, 11, 1))
                .status(PlanningDemandStatus.OPEN)
                .referenceType(PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE)
                .referenceId(salesOrderLineId.toString())
                .build());
        run = persist(MrpRun.builder()
                .company(company).plant(plant)
                .horizonStartDate(LocalDate.of(2026, 10, 14)).horizonEndDate(LocalDate.of(2026, 11, 12))
                .status(MrpRunStatus.COMPLETED)
                .runKind(MrpRunKind.COMMERCIAL)
                .sourceVersion("a".repeat(64))
                .inputSnapshot(new MrpRunInputSnapshot(List.of(salesOrderLineId),
                        List.of(new MrpRunInputSnapshot.ItemWarehouse(product.getItemId(),
                                warehouse.getWarehouseId(), warehouse.getWarehouseId())), false))
                .build());
        persist(MrpRunDemand.builder().mrpRun(run).planningDemand(demand).item(product)
                .requiredQuantity(new BigDecimal("70")).dueDate(demand.getDueDate()).priority(100).build());
        rootLine = persist(line(product, null, demand, 0, "70", WarehouseResolutionSource.RUN_ITEM_MAPPING));
        componentLine = persist(line(raw, rootLine, demand, 1, "140", WarehouseResolutionSource.ITEM_WAREHOUSE_DEFAULT));
        makeSuggestion = persist(SupplySuggestion.builder()
                .mrpRun(run).requirementLine(rootLine).company(company).plant(plant)
                .warehouse(warehouse).outputWarehouse(warehouse).item(product)
                .suggestionType(SupplySuggestionType.WORK_ORDER)
                .suggestedQuantity(new BigDecimal("70"))
                .neededByDate(demand.getDueDate()).suggestedOrderDate(demand.getDueDate())
                .build());
        entityManager.clear();
    }

    @Test
    void inputSnapshotAndRunKind_roundTripThroughJsonb() {
        MrpRun reloaded = mrpRunRepository.findById(run.getMrpRunId()).orElseThrow();

        assertThat(reloaded.getRunKind()).isEqualTo(MrpRunKind.COMMERCIAL);
        assertThat(reloaded.getConversionVersion()).isZero();
        assertThat(reloaded.getInputSnapshot().salesOrderLineIds()).containsExactly(salesOrderLineId);
        assertThat(reloaded.getInputSnapshot().itemWarehouseMappings()).singleElement()
                .satisfies(m -> assertThat(m.demandWarehouseId()).isEqualTo(warehouse.getWarehouseId()));
    }

    /** The source version is recomputed from exactly these two queries; they must find the run's own inputs. */
    @Test
    void sourceVersionInputs_areTheRunsSalesOrderLinesAndItsManufacturableItems() {
        assertThat(mrpRunDemandRepository.findDemandReferenceIds(run.getMrpRunId(),
                PlanningDemandService.REFERENCE_TYPE_SALES_ORDER_LINE))
                .containsExactly(salesOrderLineId.toString());
        assertThat(requirementLineRepository.findDistinctItemIdsByRunAndTypes(run.getMrpRunId(),
                PlanningSourceVersionCalculator.MANUFACTURABLE_TYPES))
                .containsExactly(product.getItemId());
    }

    @Test
    void ledgerSum_isPerProposal_andTheProposalBatchFetchesItsRequirementAndDemand() {
        persist(conversion("40", 1, null));
        persist(conversion("20", 1, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(conversionRepository.sumBySuggestionIds(List.of(makeSuggestion.getSupplySuggestionId())))
                .singleElement()
                .satisfies(row -> assertThat(row.getQuantity()).isEqualByComparingTo("60"));
        List<SupplySuggestion> suggestions =
                supplySuggestionRepository.findByRequirementLineIds(List.of(rootLine.getMrpRequirementLineId(),
                        componentLine.getMrpRequirementLineId()));
        assertThat(suggestions).singleElement().satisfies(s -> {
            assertThat(s.getRequirementLine().getSourceDemand().getReferenceId()).isEqualTo(salesOrderLineId.toString());
            assertThat(s.getOutputWarehouse().getWarehouseId()).isEqualTo(warehouse.getWarehouseId());
        });
    }

    /** A replayed key finds its rows; the same key cannot be written twice for one entry, NULL keys can. */
    @Test
    void idempotencyKey_isUniquePerRunAndEntry_butNullKeysNeverCollide() {
        persist(conversion("10", 1, "conv-1"));
        persist(conversion("10", 1, null));
        persist(conversion("10", 1, null));
        entityManager.flush();

        assertThat(conversionRepository.findByMrpRunIdAndIdempotencyKeyOrderByEntryNoAsc(run.getMrpRunId(), "conv-1"))
                .hasSize(1);
        assertThatThrownBy(() -> {
            persist(conversion("10", 1, "conv-1"));
            entityManager.flush();
        }).hasStackTraceContaining("uk_supply_suggestion_conversions_run_key_entry");
    }

    @Test
    void ledgerRow_withAZeroQuantity_isRejectedByTheDatabase() {
        assertThatThrownBy(() -> {
            persist(conversion("0", 1, null));
            entityManager.flush();
        }).isInstanceOf(Exception.class)
                .hasStackTraceContaining("chk_supply_suggestion_conversions_quantity");
    }

    private SupplySuggestionConversion conversion(String quantity, int entryNo, String key) {
        return SupplySuggestionConversion.builder()
                .mrpRunId(run.getMrpRunId())
                .supplySuggestionId(makeSuggestion.getSupplySuggestionId())
                .requirementLineId(rootLine.getMrpRequirementLineId())
                .conversionBatchId(UUID.randomUUID())
                .entryNo(entryNo)
                .quantity(new BigDecimal(quantity))
                .targetType(SupplyConversionTargetType.WORK_ORDER)
                .targetId(UUID.randomUUID())
                .targetCode("MRP-TEST")
                .conversionVersion(1)
                .idempotencyKey(key)
                .build();
    }

    private MrpRequirementLine line(Item item, MrpRequirementLine parent, PlanningDemand demand, int level,
                                    String quantity, WarehouseResolutionSource source) {
        return MrpRequirementLine.builder()
                .mrpRun(run).parentRequirementLine(parent).sourceDemand(demand).item(item).warehouse(warehouse)
                .requirementLevel(level)
                .grossRequiredQuantity(new BigDecimal(quantity))
                .netRequiredQuantity(new BigDecimal(quantity))
                .dueDate(LocalDate.of(2026, 11, 1))
                .requirementStatus(MrpRequirementStatus.SHORTAGE)
                .warehouseResolutionSource(source)
                .build();
    }

    // @DataJpaTest does not load JpaAuditingConfig, so the NOT NULL audit columns are set here.
    private <T extends BaseEntity> T persist(T entity) {
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entityManager.persistFlushFind(entity);
    }
}
