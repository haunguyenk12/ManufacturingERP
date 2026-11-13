package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.module.inventory.service.StockBalanceReservationService;
import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.common.context.TraceIdProvider;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.common.web.PlantContextResolver;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.dto.StockBalanceAggregateResponse;
import com.erp.manufacturing.module.inventory.dto.StockIssueRequest;
import com.erp.manufacturing.module.inventory.dto.StockMovementResponse;
import com.erp.manufacturing.module.inventory.dto.StockTransferLineRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferRequest;
import com.erp.manufacturing.module.inventory.dto.StockTransferResponse;
import com.erp.manufacturing.module.inventory.mapper.InventoryMapper;
import com.erp.manufacturing.module.inventory.repository.ItemRepository;
import com.erp.manufacturing.module.inventory.service.InventoryAvailabilityService;
import com.erp.manufacturing.module.inventory.service.InventoryMovementService;
import com.erp.manufacturing.module.inventory.service.InventoryReceiveCommand;
import com.erp.manufacturing.module.inventory.service.InventoryService;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.inventory.service.LotQcOwnershipGuard;
import com.erp.manufacturing.module.inventory.service.StockTransferService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.repository.CompanyRepository;
import com.erp.manufacturing.module.organization.repository.PlantRepository;
import com.erp.manufacturing.module.organization.repository.WarehouseRepository;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.SupplierRepository;
import com.erp.manufacturing.module.purchasing.service.query.PurchaseQcLookupService;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderComponentLine;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.core.WorkOrderVersionRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationCreateRequest;
import com.erp.manufacturing.module.workorder.dto.execution.MaterialReservationResponse;
import com.erp.manufacturing.module.workorder.mapper.ManufacturingExecutionMapper;
import com.erp.manufacturing.module.workorder.repository.WorkOrderRepository;
import com.erp.manufacturing.module.workorder.service.WorkOrderIdempotency;
import com.erp.manufacturing.module.workorder.service.execution.MaterialReservationService;
import com.erp.manufacturing.module.workorder.service.query.LotQcOriginLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CF-12 follow-up (FE_REQUEST_PURCHASE_QUARANTINE_GUARDS_2026-10-30_v3 §3): the purchase-QC hold of a
 * <b>non-lot-tracked</b> item lives only on the balance row ({@code qualityHoldQuantity}); there is no lot for the
 * CF-1 guard to look at. Every generic command that takes stock out of free stock — a transfer, a work-order
 * reservation (manual and automatic) and a manual inventory issue — must read the same canonical availability
 * ({@code quantity − reserved − qualityHold}) and never spend the hold.
 *
 * <p>Fixture (fresh for every test): 25 received on a purchase receipt into the hold, QC 20 accepted / 5 rejected
 * ⇒ on hand 25, hold 5, reserved 0, available 20. The {@code *_pendingHold_*} variants decide 20 / 0 instead, so the
 * 5 still on hold are pending rather than rejected.
 *
 * <p>Services are the ones the endpoints call ({@link StockTransferService#create}, {@link InventoryService#issue},
 * {@link MaterialReservationService#reserve} / {@link MaterialReservationService#reserveAutomatically}); method
 * security is not part of this slice — what is checked is the stock guard each one reaches with a valid command.
 * The {@code @DataJpaTest} wrapping transaction is off: each call commits or rolls back on its own.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({StockBalanceReservationService.class, PurchaseQualityService.class, GoodsReceiptDraftService.class, GoodsReceiptPoster.class, PurchasingIdempotency.class,
        PurchasingMapper.class, IdempotencySupport.class, TraceIdProvider.class, InventoryMovementService.class,
        ItemLookupService.class, OrganizationLookupService.class, UserLookupService.class, LotQcOwnershipGuard.class,
        PurchaseQcLookupService.class, LotQcOriginLookupService.class, InventoryService.class, InventoryMapper.class,
        StockTransferService.class, PlantContextResolver.class, MaterialReservationService.class,
        ManufacturingExecutionMapper.class, WorkOrderIdempotency.class, InventoryAvailabilityService.class,
        PurchaseQcHoldGuardsIT.PackagePrivateBeans.class, PurchaseQcHoldGuardsIT.Config.class})
class PurchaseQcHoldGuardsIT extends AbstractPostgresIntegrationTest {

    private static final String ZONE = "Asia/Ho_Chi_Minh";

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "auditorAware")
    static class Config {
        @Bean
        AuditorAware<UUID> auditorAware() {
            return Optional::empty;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    /** {@code WorkOrderExecutionSupport} is package-private in another package: imported by name. */
    static class PackagePrivateBeans implements ImportSelector {
        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            return new String[]{"com.erp.manufacturing.module.workorder.service.execution.WorkOrderExecutionSupport"};
        }
    }

    @Autowired PurchaseQualityService qualityService;
    @Autowired GoodsReceiptDraftService receiptService;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired PlantRepository plantRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired BomHeaderRepository bomHeaderRepository;
    @Autowired BomLineRepository bomLineRepository;
    @Autowired WorkOrderRepository workOrderRepository;
    @Autowired InventoryMovementService movementService;
    @Autowired InventoryService inventoryService;
    @Autowired StockTransferService transferService;
    @Autowired MaterialReservationService reservationService;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- transfer (R01)

    /** T01: 21 asked, 20 free — refused before anything is written; the hold and the QC source stay as they were. */
    @Test
    void transfer_moreThanFreeStock_isRefused_andWritesNothing() {
        Held h = held(false);

        assertInsufficient(() -> transferService.create(transfer(h, h.item(), "21"), "T01-" + h.suffix(), null),
                "lines[0].quantity");

        assertNothingMoved(h);
        assertThat(jdbc.queryForObject("select count(*) from stock_transfers where plant_id = ?", Integer.class,
                h.f().plant().getPlantId())).isZero();
        assertThat(balanceRow(h.item(), h.wh2())).isNull();
    }

    /** T01 with the 5 still pending QC (decided 20 / 0) instead of rejected. */
    @Test
    void transfer_moreThanFreeStock_pendingHold_isRefused_andWritesNothing() {
        Held h = held(true);

        assertInsufficient(() -> transferService.create(transfer(h, h.item(), "21"), "T01P-" + h.suffix(), null),
                "lines[0].quantity");

        assertNothingMoved(h);
        assertThat(balanceRow(h.item(), h.wh2())).isNull();
    }

    /** T02: 5 of free stock move; WH1 keeps the hold whole; WH2 receives 5 free; a replay of the key posts nothing more. */
    @Test
    void transfer_ofFreeStock_movesOnlyFreeStock_andAReplayDoesNotPostTwice() {
        Held h = held(false);
        String key = "T02-" + h.suffix();

        StockTransferResponse response = transferService.create(transfer(h, h.item(), "5"), key, null);

        assertBalance(h.item(), h.f().warehouse(), "20", "0", "5");
        assertAvailable(h.item(), h.f().warehouse(), "15");
        assertBalance(h.item(), h.wh2(), "5", "0", "0");
        assertAvailable(h.item(), h.wh2(), "5");
        assertThat(movementCount(h.item())).isEqualTo(h.movementsBefore() + 2);
        assertQcSourceUnchanged(h);

        StockTransferResponse replay = transferService.create(transfer(h, h.item(), "5"), key, null);

        assertThat(replay.transferId()).isEqualTo(response.transferId());
        assertBalance(h.item(), h.f().warehouse(), "20", "0", "5");
        assertBalance(h.item(), h.wh2(), "5", "0", "0");
        assertThat(movementCount(h.item())).isEqualTo(h.movementsBefore() + 2);
    }

    /**
     * R05: a two-line transfer whose first line is valid free stock of another item and whose <b>last</b> line asks
     * for 21 of the held item. The whole document is refused — the first line moves nothing either — and the error
     * names the last line only.
     */
    @Test
    void transfer_multiLine_lastLineEatingIntoTheHold_rollsBackTheWholeDocument() {
        Held h = held(false);
        Item other = item(h.f());
        movementService.receive(new InventoryReceiveCommand(other.getItemId(), h.f().warehouse().getWarehouseId(), null,
                null, new BigDecimal("10"), "Opening stock", null, null, null, null, null), "R05-RCV-" + h.suffix());
        int otherMovementsBefore = movementCount(other);

        StockTransferRequest request = new StockTransferRequest(h.f().warehouse().getWarehouseId(),
                h.wh2().getWarehouseId(), "Move", List.of(
                        new StockTransferLineRequest(other.getItemId(), null, new BigDecimal("3")),
                        new StockTransferLineRequest(h.item().getItemId(), null, new BigDecimal("21"))));
        assertThatThrownBy(() -> transferService.create(request, "R05-" + h.suffix(), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].quantity");
                });

        assertNothingMoved(h);
        assertBalance(other, h.f().warehouse(), "10", "0", "0");
        assertThat(balanceRow(other, h.wh2())).isNull();
        assertThat(movementCount(other)).isEqualTo(otherMovementsBefore);
        assertThat(jdbc.queryForObject("select count(*) from stock_transfers where idempotency_key = ?", Integer.class,
                "R05-" + h.suffix())).isZero();
    }

    // ---------------------------------------------------------------- reservation (R02)

    /** H01: a valid RELEASED work order needing 30 at WH1; a manual reservation of 21 is refused, nothing written. */
    @Test
    void manualReservation_moreThanFreeStock_isRefused_andWritesNothing() {
        Held h = held(false);
        WorkOrder workOrder = workOrder(h, "30");
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);

        assertInsufficient(() -> reservationService.reserve(workOrder.getWorkOrderId(),
                reservation(h, line, "21", workOrder.getVersion()), "H01-" + h.suffix()), null);

        assertNothingMoved(h);
        assertThat(reservationCount(workOrder)).isZero();
        assertThat(workOrderVersion(workOrder)).isEqualTo(workOrder.getVersion());
    }

    /** H01 with the 5 still pending QC. */
    @Test
    void manualReservation_moreThanFreeStock_pendingHold_isRefused_andWritesNothing() {
        Held h = held(true);
        WorkOrder workOrder = workOrder(h, "30");
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);

        assertInsufficient(() -> reservationService.reserve(workOrder.getWorkOrderId(),
                reservation(h, line, "21", workOrder.getVersion()), "H01P-" + h.suffix()), null);

        assertNothingMoved(h);
        assertThat(reservationCount(workOrder)).isZero();
    }

    /** H02: 5 reserved from free stock; the hold is untouched; a replay of the key reserves nothing more. */
    @Test
    void manualReservation_ofFreeStock_reservesOnlyFreeStock_andAReplayDoesNotReserveTwice() {
        Held h = held(false);
        WorkOrder workOrder = workOrder(h, "30");
        WorkOrderComponentLine line = workOrder.getComponentLines().get(0);
        MaterialReservationCreateRequest request = reservation(h, line, "5", workOrder.getVersion());

        MaterialReservationResponse created = reservationService.reserve(workOrder.getWorkOrderId(), request,
                "H02-" + h.suffix());

        assertBalance(h.item(), h.f().warehouse(), "25", "5", "5");
        assertAvailable(h.item(), h.f().warehouse(), "15");
        assertThat(movementCount(h.item())).isEqualTo(h.movementsBefore());
        assertQcSourceUnchanged(h);

        MaterialReservationResponse replay = reservationService.reserve(workOrder.getWorkOrderId(), request,
                "H02-" + h.suffix());

        assertThat(replay.reservationId()).isEqualTo(created.reservationId());
        assertThat(reservationCount(workOrder)).isEqualTo(1);
        assertBalance(h.item(), h.f().warehouse(), "25", "5", "5");
    }

    /**
     * Automatic reservation is partial by contract (spec §4.2): the work order needs 30, only 20 are free — it reserves
     * exactly 20 and stops; the 5 on hold are never taken.
     */
    @Test
    void automaticReservation_capsAtFreeStock_andNeverTakesTheHold() {
        Held h = held(false);
        WorkOrder workOrder = workOrder(h, "30");

        List<MaterialReservationResponse> created = reservationService.reserveAutomatically(workOrder.getWorkOrderId(),
                new WorkOrderVersionRequest(workOrder.getVersion()), "HA-" + h.suffix());

        assertThat(created).singleElement().satisfies(r -> assertThat(r.quantity()).isEqualByComparingTo("20"));
        assertBalance(h.item(), h.f().warehouse(), "25", "20", "5");
        assertAvailable(h.item(), h.f().warehouse(), "0");
        assertQcSourceUnchanged(h);
    }

    /** Same with the 5 still pending QC. */
    @Test
    void automaticReservation_pendingHold_capsAtFreeStock() {
        Held h = held(true);
        WorkOrder workOrder = workOrder(h, "30");

        List<MaterialReservationResponse> created = reservationService.reserveAutomatically(workOrder.getWorkOrderId(),
                new WorkOrderVersionRequest(workOrder.getVersion()), "HAP-" + h.suffix());

        assertThat(created).singleElement().satisfies(r -> assertThat(r.quantity()).isEqualByComparingTo("20"));
        assertBalance(h.item(), h.f().warehouse(), "25", "20", "5");
    }

    // ---------------------------------------------------------------- generic issue (R03)

    /** I01: a manual issue of 21 (POST /v1/inventory/issue) is refused; nothing changes. */
    @Test
    void issue_moreThanFreeStock_isRefused_andWritesNothing() {
        Held h = held(false);

        assertInsufficient(() -> inventoryService.issue(issue(h, "21"), "I01-" + h.suffix()), null);

        assertNothingMoved(h);
    }

    /** I01 with the 5 still pending QC. */
    @Test
    void issue_moreThanFreeStock_pendingHold_isRefused_andWritesNothing() {
        Held h = held(true);

        assertInsufficient(() -> inventoryService.issue(issue(h, "21"), "I01P-" + h.suffix()), null);

        assertNothingMoved(h);
    }

    /** I02: 5 issued from free stock: exactly one ISSUE posting; a replay of the key posts nothing more. */
    @Test
    void issue_ofFreeStock_takesOnlyFreeStock_andAReplayDoesNotPostTwice() {
        Held h = held(false);

        StockMovementResponse issued = inventoryService.issue(issue(h, "5"), "I02-" + h.suffix());

        assertBalance(h.item(), h.f().warehouse(), "20", "0", "5");
        assertAvailable(h.item(), h.f().warehouse(), "15");
        assertThat(movementCount(h.item())).isEqualTo(h.movementsBefore() + 1);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where item_id = ? and movement_type = 'ISSUE'",
                Integer.class, h.item().getItemId())).isEqualTo(1);
        assertQcSourceUnchanged(h);

        StockMovementResponse replay = inventoryService.issue(issue(h, "5"), "I02-" + h.suffix());

        assertThat(replay.movementId()).isEqualTo(issued.movementId());
        assertBalance(h.item(), h.f().warehouse(), "20", "0", "5");
        assertThat(movementCount(h.item())).isEqualTo(h.movementsBefore() + 1);
    }

    // ---------------------------------------------------------------- fixture

    private record Fixture(Company company, Plant plant, Warehouse warehouse, Supplier supplier, String suffix) {}

    /** The held position plus what it looked like right after QC — the baseline every refused case compares to. */
    private record Held(Fixture f, Warehouse wh2, Item item, UUID receiptLineId, int movementsBefore,
                        long balanceVersion, int decisionCount) {
        String suffix() {
            return f.suffix();
        }
    }

    private Held held(boolean pending) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(Company.builder().code("QH_" + suffix).name("Co").status(OrganizationStatus.ACTIVE).build());
        Plant plant = plantRepository.save(Plant.builder().company(company).code("P_" + suffix).name("Plant").timezone(ZONE)
                .status(OrganizationStatus.ACTIVE).build());
        Warehouse wh1 = warehouseRepository.save(Warehouse.builder().plant(plant).code("W1_" + suffix).name("Wh 1")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        Warehouse wh2 = warehouseRepository.save(Warehouse.builder().plant(plant).code("W2_" + suffix).name("Wh 2")
                .type(WarehouseType.GENERAL).status(OrganizationStatus.ACTIVE).build());
        Supplier supplier = supplierRepository.save(Supplier.builder().company(company).code("S_" + suffix).name("Acme")
                .status(SupplierStatus.ACTIVE).build());
        Fixture f = new Fixture(company, plant, wh1, supplier, suffix);
        Item item = item(f);

        GoodsReceiptResponse receipt = posted(order(f, item), "25");
        UUID lineId = receipt.lines().get(0).goodsReceiptLineId();
        qualityService.decide(receipt.goodsReceiptId(), lineId, pending
                ? new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("20"), BigDecimal.ZERO, null)
                : new PurchaseQcDecisionRequest(receipt.version(), new BigDecimal("20"), new BigDecimal("5"), "5 kg failed"),
                null);

        assertBalance(item, wh1, "25", "0", "5");
        assertAvailable(item, wh1, "20");
        Map<String, Object> source = qcSource(lineId);
        assertThat((BigDecimal) source.get("accepted_quantity")).isEqualByComparingTo("20");
        assertThat((BigDecimal) source.get("rejected_quantity")).isEqualByComparingTo(pending ? "0" : "5");
        return new Held(f, wh2, item, lineId, movementCount(item), balanceVersion(item, wh1),
                ((Number) source.get("decisions")).intValue());
    }

    private Item item(Fixture f) {
        return itemRepository.save(Item.builder().company(f.company()).code("I_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Raw").type(ItemType.RAW_MATERIAL).unit("KG").lotTracked(false).status(ItemStatus.ACTIVE).build());
    }

    private PurchaseOrder order(Fixture f, Item item) {
        LocalDate today = LocalDate.now(ZoneId.of(ZONE));
        PurchaseOrder order = PurchaseOrder.builder().company(f.company()).plant(f.plant()).warehouse(f.warehouse())
                .supplier(f.supplier()).purchaseOrderNo("PO-" + UUID.randomUUID().toString().substring(0, 8))
                .status(PurchaseOrderStatus.CONFIRMED).orderDate(today.minusDays(2)).expectedDate(today).build();
        order.getLines().add(PurchaseOrderLine.builder().purchaseOrder(order).item(item)
                .orderedQuantity(new BigDecimal("40")).receivedQuantity(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("10")).currencyCode("VND").expectedDate(today).build());
        return orderRepository.saveAndFlush(order);
    }

    private GoodsReceiptResponse posted(PurchaseOrder order, String quantity) {
        UUID orderLineId = jdbc.queryForObject(
                "select purchase_order_line_id from purchase_order_lines where purchase_order_id = ?",
                UUID.class, order.getPurchaseOrderId());
        GoodsReceiptResponse draft = receiptService.createDraft(new GoodsReceiptDraftCreateRequest(order.getPurchaseOrderId(),
                LocalDate.now(ZoneId.of(ZONE)), null, null, List.of(new GoodsReceiptDraftLineRequest(null, orderLineId,
                        new BigDecimal(quantity), null, null))), null);
        return receiptService.post(draft.goodsReceiptId(), new GoodsReceiptVersionRequest(draft.version()), null);
    }

    /** A RELEASED work order whose only component is the held item at WH1, needing {@code required}. */
    private WorkOrder workOrder(Held h, String required) {
        Fixture f = h.f();
        Item product = itemRepository.save(Item.builder().company(f.company()).code("FG_" + UUID.randomUUID().toString().substring(0, 8))
                .name("Product").type(ItemType.FINISHED_GOOD).unit("EA").status(ItemStatus.ACTIVE).build());
        BomHeader bom = bomHeaderRepository.save(BomHeader.builder().company(f.company()).parentItem(product).revision("R1")
                .bomCode("BOM-" + h.suffix()).versionNumber(1).status(BomStatus.ACTIVE).build());
        BomLine bomLine = bomLineRepository.save(BomLine.builder().bom(bom).componentItem(h.item()).lineNo(1)
                .quantityPer(new BigDecimal("3")).build());
        WorkOrder workOrder = WorkOrder.builder().company(f.company()).plant(f.plant())
                .workOrderNo("WO-" + UUID.randomUUID().toString().substring(0, 8)).productItem(product).bom(bom)
                .bomRevision("R1").outputWarehouse(f.warehouse()).plannedQuantity(new BigDecimal("10"))
                .completedQuantity(BigDecimal.ZERO).actualGoodQuantity(BigDecimal.ZERO)
                .actualScrapQuantity(BigDecimal.ZERO).actualReworkQuantity(BigDecimal.ZERO)
                .status(WorkOrderStatus.RELEASED).build();
        workOrder.getComponentLines().add(WorkOrderComponentLine.builder().workOrder(workOrder).bomLine(bomLine).lineNo(1)
                .componentItem(h.item()).warehouse(f.warehouse()).quantityPer(new BigDecimal("3"))
                .requiredQuantity(new BigDecimal(required)).build());
        return workOrderRepository.saveAndFlush(workOrder);
    }

    private static StockTransferRequest transfer(Held h, Item item, String quantity) {
        return new StockTransferRequest(h.f().warehouse().getWarehouseId(), h.wh2().getWarehouseId(), "Move to line side",
                List.of(new StockTransferLineRequest(item.getItemId(), null, new BigDecimal(quantity))));
    }

    private static MaterialReservationCreateRequest reservation(Held h, WorkOrderComponentLine line, String quantity,
                                                                Long version) {
        return new MaterialReservationCreateRequest(line.getComponentLineId(), h.f().warehouse().getWarehouseId(), null,
                new BigDecimal(quantity), version);
    }

    private static StockIssueRequest issue(Held h, String quantity) {
        return new StockIssueRequest(h.item().getItemId(), h.f().warehouse().getWarehouseId(), null, null,
                new BigDecimal(quantity), "Manual issue", null, null);
    }

    // ---------------------------------------------------------------- assertions

    private static void assertInsufficient(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.INSUFFICIENT_STOCK);
                    assertThat(((AppException) ex).getErrorCode().code()).isEqualTo("INSUFFICIENT_AVAILABLE_STOCK");
                    assertThat(((AppException) ex).getErrorCode().status().value()).isEqualTo(409);
                    if (field != null) {
                        assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys(field);
                    }
                });
    }

    /** A refused command: WH1 is exactly as QC left it — row version too — no movement, the QC source untouched. */
    private void assertNothingMoved(Held h) {
        assertBalance(h.item(), h.f().warehouse(), "25", "0", "5");
        assertAvailable(h.item(), h.f().warehouse(), "20");
        assertThat(balanceVersion(h.item(), h.f().warehouse())).isEqualTo(h.balanceVersion());
        assertThat(movementCount(h.item())).isEqualTo(h.movementsBefore());
        assertQcSourceUnchanged(h);
    }

    private void assertQcSourceUnchanged(Held h) {
        Map<String, Object> source = qcSource(h.receiptLineId());
        assertThat((BigDecimal) source.get("received_quantity")).isEqualByComparingTo("25");
        assertThat((BigDecimal) source.get("accepted_quantity")).isEqualByComparingTo("20");
        assertThat(((Number) source.get("decisions")).intValue()).isEqualTo(h.decisionCount());
    }

    private Map<String, Object> qcSource(UUID lineId) {
        return jdbc.queryForMap("""
                select l.received_quantity, l.accepted_quantity, l.rejected_quantity,
                       (select count(*) from purchase_quality_decisions d where d.goods_receipt_line_id = l.goods_receipt_line_id)
                           as decisions
                from goods_receipt_lines l where l.goods_receipt_line_id = ?
                """, lineId);
    }

    private void assertBalance(Item item, Warehouse warehouse, String quantity, String reserved, String qualityHold) {
        Map<String, Object> row = balanceRow(item, warehouse);
        assertThat(row).isNotNull();
        assertThat((BigDecimal) row.get("quantity")).isEqualByComparingTo(quantity);
        assertThat((BigDecimal) row.get("reserved_quantity")).isEqualByComparingTo(reserved);
        assertThat((BigDecimal) row.get("quality_hold_quantity")).isEqualByComparingTo(qualityHold);
    }

    /** The canonical read the inventory screen uses (GET /v1/inventory/balances/aggregate). */
    private void assertAvailable(Item item, Warehouse warehouse, String available) {
        List<StockBalanceAggregateResponse> rows = inventoryService.listAggregateBalances(warehouse.getWarehouseId(),
                item.getItemId(), null, PageRequest.of(0, 20)).content();
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.availableQuantity()).isEqualByComparingTo(available));
    }

    private Map<String, Object> balanceRow(Item item, Warehouse warehouse) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select quantity, reserved_quantity, quality_hold_quantity from stock_balances
                where item_id = ? and warehouse_id = ? and lot_id is null
                """, item.getItemId(), warehouse.getWarehouseId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long balanceVersion(Item item, Warehouse warehouse) {
        return jdbc.queryForObject("select version from stock_balances where item_id = ? and warehouse_id = ? and lot_id is null",
                Long.class, item.getItemId(), warehouse.getWarehouseId());
    }

    private int movementCount(Item item) {
        return jdbc.queryForObject("select count(*) from stock_movements where item_id = ?", Integer.class, item.getItemId());
    }

    private int reservationCount(WorkOrder workOrder) {
        return jdbc.queryForObject("select count(*) from material_reservations where work_order_id = ?", Integer.class,
                workOrder.getWorkOrderId());
    }

    private long workOrderVersion(WorkOrder workOrder) {
        return jdbc.queryForObject("select version from work_orders where work_order_id = ?", Long.class,
                workOrder.getWorkOrderId());
    }
}
