package com.erp.manufacturing.module.purchasing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.idempotency.IdempotencySupport;
import com.erp.manufacturing.module.planning.dto.PurchaseOrderPlanningSource;
import com.erp.manufacturing.module.planning.service.PurchaseOrderPlanningLinkService;
import com.erp.manufacturing.module.inventory.domain.*;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.*;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.purchasing.domain.*;
import com.erp.manufacturing.module.purchasing.dto.*;
import com.erp.manufacturing.module.purchasing.mapper.PurchasingMapper;
import com.erp.manufacturing.module.purchasing.repository.PurchaseOrderRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchaseRequisitionRepository;
import com.erp.manufacturing.module.purchasing.repository.PurchasingIdempotencyRecordRepository;
import com.erp.manufacturing.module.user.service.UserLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseOrderService tests")
class PurchaseOrderServiceTest {

    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock PurchaseRequisitionRepository purchaseRequisitionRepository;
    @Mock OrganizationLookupService organizationLookupService;
    @Mock ItemLookupService itemLookupService;
    @Mock SupplierService supplierService;
    @Mock PurchaseOrderPlanningLinkService planningLinkService;
    @Mock PurchasingIdempotencyRecordRepository idempotencyRecordRepository;
    @Mock UserLookupService userLookupService;
    @Mock AuditorAware<UUID> auditorAware;

    PurchaseOrderService service;
    IdempotencySupport idempotencySupport;

    @BeforeEach
    void setUp() {
        idempotencySupport = new IdempotencySupport(new ObjectMapper().registerModule(new JavaTimeModule()));
        service = new PurchaseOrderService(
                purchaseOrderRepository,
                purchaseRequisitionRepository,
                organizationLookupService,
                itemLookupService,
                supplierService,
                new PurchasingMapper(),
                planningLinkService,
                new PurchasingIdempotency(idempotencyRecordRepository, idempotencySupport),
                userLookupService,
                auditorAware);
    }

    @Test
    void createPurchaseOrder_success() {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        Item item = item(UUID.randomUUID(), company);
        when(supplierService.findActiveSupplier(supplier.getSupplierId())).thenReturn(supplier);
        when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        when(purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(company.getCompanyId(), "PO-001"))
                .thenReturn(false);
        when(itemLookupService.getActiveItem(item.getItemId())).thenReturn(item);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setPurchaseOrderId(UUID.randomUUID());
            order.getLines().forEach(line -> line.setPurchaseOrderLineId(UUID.randomUUID()));
            return order;
        });

        PurchaseOrderResponse response = service.create(new PurchaseOrderCreateRequest(
                company.getCompanyId(),
                plant.getPlantId(),
                warehouse.getWarehouseId(),
                supplier.getSupplierId(),
                "PO-001",
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                null,
                null,
                List.of(new PurchaseOrderLineRequest(
                        null, item.getItemId(), new BigDecimal("10"), BigDecimal.ZERO, "USD", null))));

        assertThat(response.status()).isEqualTo(PurchaseOrderStatus.DRAFT.name());
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).orderedQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void sendDraftOrder_success() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.DRAFT);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        // G7a: saveAndFlush, so the response version is the one persisted (§0.39).
        when(purchaseOrderRepository.saveAndFlush(order)).thenReturn(order);

        PurchaseOrderResponse response = service.send(order.getPurchaseOrderId());

        assertThat(response.status()).isEqualTo(PurchaseOrderStatus.CONFIRMED.name());
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void cancelSentOrder_fails() {
        PurchaseOrder order = purchaseOrder(PurchaseOrderStatus.CONFIRMED);
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(order.getPurchaseOrderId(), null, null))
                .isInstanceOf(AppException.class)
                // D7: purchase order status conflict is 409 STATE_CONFLICT, not 422
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    // ── G7a: drafts ────────────────────────────────────────────────────────

    private record DraftFixture(Company company, Plant plant, Warehouse warehouse, Supplier supplier, Item item) {}

    private DraftFixture draftFixture(ItemType type) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        Item item = item(UUID.randomUUID(), company, type);
        lenient().when(supplierService.findActiveSupplier(supplier.getSupplierId())).thenReturn(supplier);
        lenient().when(organizationLookupService.getActiveCompany(company.getCompanyId())).thenReturn(company);
        lenient().when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        lenient().when(organizationLookupService.getActiveWarehouse(warehouse.getWarehouseId())).thenReturn(warehouse);
        lenient().when(itemLookupService.getActiveItem(item.getItemId())).thenReturn(item);
        lenient().when(purchaseOrderRepository.saveAndFlush(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            if (order.getPurchaseOrderId() == null) {
                order.setPurchaseOrderId(UUID.randomUUID());
            }
            order.getLines().forEach(line -> {
                if (line.getPurchaseOrderLineId() == null) {
                    line.setPurchaseOrderLineId(UUID.randomUUID());
                }
            });
            return order;
        });
        return new DraftFixture(company, plant, warehouse, supplier, item);
    }

    private static PurchaseOrderDraftCreateRequest draftRequest(DraftFixture f, LocalDate orderDate, LocalDate expected) {
        return new PurchaseOrderDraftCreateRequest(f.company().getCompanyId(), f.plant().getPlantId(),
                f.supplier().getSupplierId(), f.warehouse().getWarehouseId(), orderDate, " REF-1 ", "",
                List.of(new PurchaseOrderDraftLineRequest(null, f.item().getItemId(), new BigDecimal("40"),
                        new BigDecimal("10"), expected)));
    }

    @Test
    @DisplayName("G7a createDraft: server number from the sequence, VND line, reference trimmed, key claimed")
    void createDraft_issuesServerNumberAndClaimsTheKey() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        when(purchaseOrderRepository.nextOrderNumber()).thenReturn(7L);
        LocalDate today = LocalDate.of(2026, 10, 14);

        PurchaseOrderResponse response = service.createDraft(draftRequest(f, today, today.plusDays(3)), "key-1");

        assertThat(response.purchaseOrderNo()).isEqualTo("PO-000007");
        assertThat(response.code()).isEqualTo("PO-000007");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.reference()).isEqualTo("REF-1");
        assertThat(response.note()).isNull();
        assertThat(response.expectedDate()).isEqualTo(today.plusDays(3));
        assertThat(response.currencyCode()).isEqualTo("VND");
        assertThat(response.lines().get(0).currencyCode()).isEqualTo("VND");
        assertThat(response.lines().get(0).openQuantity()).isEqualByComparingTo("40");
        assertThat(response.lines().get(0).uomCode()).isEqualTo("EA");
        ArgumentCaptor<PurchasingIdempotencyRecord> record = ArgumentCaptor.forClass(PurchasingIdempotencyRecord.class);
        verify(idempotencyRecordRepository).saveAndFlush(record.capture());
        assertThat(record.getValue().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(record.getValue().getCommand()).isEqualTo(PurchasingCommand.PURCHASE_ORDER_CREATE_DRAFT);
        assertThat(record.getValue().getResourceId()).isEqualTo(response.purchaseOrderId());
    }

    @Test
    @DisplayName("G7a createDraft: a sequence value an older client-chosen number took is skipped")
    void createDraft_numberTakenByLegacyOrder_isSkipped() {
        DraftFixture f = draftFixture(ItemType.CONSUMABLE);
        when(purchaseOrderRepository.nextOrderNumber()).thenReturn(1L, 2L);
        when(purchaseOrderRepository.existsByCompanyCompanyIdAndPurchaseOrderNo(f.company().getCompanyId(), "PO-000001"))
                .thenReturn(true);
        LocalDate today = LocalDate.of(2026, 10, 14);

        PurchaseOrderResponse response = service.createDraft(draftRequest(f, today, today), null);

        assertThat(response.purchaseOrderNo()).isEqualTo("PO-000002");
        verifyNoInteractions(idempotencyRecordRepository);
    }

    @Test
    @DisplayName("G7a createDraft: a line date before the order date is a field error before any lookup")
    void createDraft_lineDateBeforeOrderDate_failsBeforeLookups() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        LocalDate today = LocalDate.of(2026, 10, 14);
        reset(supplierService, organizationLookupService, itemLookupService, purchaseOrderRepository);

        assertThatThrownBy(() -> service.createDraft(draftRequest(f, today, today.minusDays(1)), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(ValidationErrorCode.INVALID_INPUT);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].expectedDate");
                });
        verifyNoInteractions(supplierService, organizationLookupService, itemLookupService, purchaseOrderRepository);
    }

    @Test
    @DisplayName("G7a createDraft: a finished good cannot be bought here (FE Q6) — per-line 422, nothing saved")
    void createDraft_finishedGood_isRejectedPerLine() {
        DraftFixture f = draftFixture(ItemType.FINISHED_GOOD);
        when(purchaseOrderRepository.nextOrderNumber()).thenReturn(1L);
        LocalDate today = LocalDate.of(2026, 10, 14);

        assertThatThrownBy(() -> service.createDraft(draftRequest(f, today, today), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].itemId");
                });
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("G7a createDraft: replaying the key with the same payload returns the order, saves nothing")
    void createDraft_replay_returnsTheOrderWithoutSaving() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        LocalDate today = LocalDate.of(2026, 10, 14);
        PurchaseOrderDraftCreateRequest request = draftRequest(f, today, today);
        PurchaseOrder existing = purchaseOrder(PurchaseOrderStatus.DRAFT);
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("key-1", PurchasingCommand.PURCHASE_ORDER_CREATE_DRAFT))
                .thenReturn(Optional.of(PurchasingIdempotencyRecord.builder()
                        .resourceId(existing.getPurchaseOrderId())
                        .payloadHash(idempotencySupport.payloadHash(request))
                        .build()));
        when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(existing.getPurchaseOrderId()))
                .thenReturn(Optional.of(existing));

        PurchaseOrderResponse response = service.createDraft(request, "key-1");

        assertThat(response.purchaseOrderId()).isEqualTo(existing.getPurchaseOrderId());
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
        verify(purchaseOrderRepository, never()).nextOrderNumber();
    }

    @Test
    @DisplayName("G7a createDraft: the same key with another payload is IDEMPOTENCY_CONFLICT")
    void createDraft_replayWithAnotherPayload_conflicts() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        LocalDate today = LocalDate.of(2026, 10, 14);
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("key-1", PurchasingCommand.PURCHASE_ORDER_CREATE_DRAFT))
                .thenReturn(Optional.of(PurchasingIdempotencyRecord.builder()
                        .resourceId(UUID.randomUUID())
                        .payloadHash(idempotencySupport.payloadHash(draftRequest(f, today, today.plusDays(1))))
                        .build()));

        assertThatThrownBy(() -> service.createDraft(draftRequest(f, today, today), "key-1"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.IDEMPOTENCY_CONFLICT));
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    // ── G7a: PATCH ─────────────────────────────────────────────────────────

    private PurchaseOrder stubbedDraft(DraftFixture f, int lines) {
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseOrderId(UUID.randomUUID())
                .company(f.company())
                .plant(f.plant())
                .warehouse(f.warehouse())
                .supplier(f.supplier())
                .purchaseOrderNo("PO-000009")
                .status(PurchaseOrderStatus.DRAFT)
                .orderDate(LocalDate.of(2026, 10, 14))
                .expectedDate(LocalDate.of(2026, 10, 14))
                .reference("KEEP-ME")
                .lines(new ArrayList<>())
                .build();
        order.setVersion(2L);
        for (int i = 0; i < lines; i++) {
            order.getLines().add(PurchaseOrderLine.builder()
                    .purchaseOrderLineId(UUID.randomUUID())
                    .purchaseOrder(order)
                    .item(f.item())
                    .orderedQuantity(new BigDecimal("10"))
                    .receivedQuantity(BigDecimal.ZERO)
                    .expectedDate(order.getExpectedDate())
                    .build());
        }
        lenient().when(purchaseOrderRepository.findWithDetailsByPurchaseOrderId(order.getPurchaseOrderId()))
                .thenReturn(Optional.of(order));
        return order;
    }

    private static PurchaseOrderUpdateRequest update(DraftFixture f, long version, String reference,
                                                     PurchaseOrderDraftLineRequest... lines) {
        return new PurchaseOrderUpdateRequest(version, f.supplier().getSupplierId(), f.warehouse().getWarehouseId(),
                LocalDate.of(2026, 10, 14), reference, null, List.of(lines));
    }

    @Test
    @DisplayName("G7a PATCH: a stale expectedVersion is CONCURRENT_MODIFICATION before anything is written")
    void updateDraft_staleVersion_failsBeforeWrite() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);

        assertThatThrownBy(() -> service.updateDraft(order.getPurchaseOrderId(), update(f, 1L, null,
                new PurchaseOrderDraftLineRequest(null, f.item().getItemId(), BigDecimal.ONE, null, LocalDate.of(2026, 10, 14))), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
        verifyNoInteractions(idempotencyRecordRepository);
    }

    @Test
    @DisplayName("G7a PATCH: lines with an id are kept (same id), new lines added, omitted lines removed; version forced")
    void updateDraft_keepsLineIds_addsAndRemoves() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 2);
        UUID keptId = order.getLines().get(0).getPurchaseOrderLineId();
        UUID droppedId = order.getLines().get(1).getPurchaseOrderLineId();
        LocalDate date = LocalDate.of(2026, 10, 20);

        PurchaseOrderResponse response = service.updateDraft(order.getPurchaseOrderId(), update(f, 2L, null,
                new PurchaseOrderDraftLineRequest(keptId, f.item().getItemId(), new BigDecimal("12.5"), new BigDecimal("3"), date),
                new PurchaseOrderDraftLineRequest(null, f.item().getItemId(), new BigDecimal("1"), null, date)), null);

        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getLines()).extracting(PurchaseOrderLine::getPurchaseOrderLineId)
                .contains(keptId).doesNotContain(droppedId);
        PurchaseOrderLine kept = order.getLines().stream()
                .filter(line -> keptId.equals(line.getPurchaseOrderLineId())).findFirst().orElseThrow();
        assertThat(kept.getOrderedQuantity()).isEqualByComparingTo("12.5");
        assertThat(kept.getUnitPrice()).isEqualByComparingTo("3");
        assertThat(order.getExpectedDate()).isEqualTo(date);
        // null reference keeps the old one (B136)
        assertThat(response.reference()).isEqualTo("KEEP-ME");
        assertThat(order.getUpdatedAt()).isNotNull();
        verify(purchaseOrderRepository).saveAndFlush(order);
    }

    @Test
    @DisplayName("G7a PATCH: an id that is not a line of this order is a field error")
    void updateDraft_foreignLineId_isAFieldError() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);

        assertThatThrownBy(() -> service.updateDraft(order.getPurchaseOrderId(), update(f, 2L, null,
                new PurchaseOrderDraftLineRequest(UUID.randomUUID(), f.item().getItemId(), BigDecimal.ONE, null,
                        LocalDate.of(2026, 10, 14))), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines[0].id"));
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("G7a PATCH: a planning-sourced draft cannot change its quantity — SOURCE_LOCKED, nothing written")
    void updateDraft_sourceLocked_quantityChange_isRejected() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        UUID lineId = order.getLines().get(0).getPurchaseOrderLineId();
        when(planningLinkService.findSourceLockedOrderIds(List.of(order.getPurchaseOrderId())))
                .thenReturn(Set.of(order.getPurchaseOrderId()));

        assertThatThrownBy(() -> service.updateDraft(order.getPurchaseOrderId(), update(f, 2L, null,
                new PurchaseOrderDraftLineRequest(lineId, f.item().getItemId(), new BigDecimal("11"), null,
                        LocalDate.of(2026, 10, 14))), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> {
                    assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.SOURCE_LOCKED);
                    assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[0].orderedQuantity");
                });
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("G7a PATCH: a planning-sourced draft may still change price, date and reference")
    void updateDraft_sourceLocked_priceAndDate_areEditable() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        UUID lineId = order.getLines().get(0).getPurchaseOrderLineId();
        when(planningLinkService.findSourceLockedOrderIds(List.of(order.getPurchaseOrderId())))
                .thenReturn(Set.of(order.getPurchaseOrderId()));

        service.updateDraft(order.getPurchaseOrderId(), update(f, 2L, "",
                new PurchaseOrderDraftLineRequest(lineId, f.item().getItemId(), new BigDecimal("10.000000"),
                        new BigDecimal("125"), LocalDate.of(2026, 10, 25))), null);

        assertThat(order.getLines().get(0).getUnitPrice()).isEqualByComparingTo("125");
        assertThat(order.getReference()).isNull();
        verify(purchaseOrderRepository).saveAndFlush(order);
    }

    @Test
    @DisplayName("G7a PATCH: a planning-sourced draft cannot add a line")
    void updateDraft_sourceLocked_addingALine_isRejected() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        UUID lineId = order.getLines().get(0).getPurchaseOrderLineId();
        when(planningLinkService.findSourceLockedOrderIds(List.of(order.getPurchaseOrderId())))
                .thenReturn(Set.of(order.getPurchaseOrderId()));
        LocalDate date = LocalDate.of(2026, 10, 14);

        assertThatThrownBy(() -> service.updateDraft(order.getPurchaseOrderId(), update(f, 2L, null,
                new PurchaseOrderDraftLineRequest(lineId, f.item().getItemId(), new BigDecimal("10"), null, date),
                new PurchaseOrderDraftLineRequest(null, f.item().getItemId(), BigDecimal.ONE, null, date)), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("lines"));
    }

    // ── G7a: confirm / cancel / close ──────────────────────────────────────

    @Test
    @DisplayName("G7a confirm: explicit 0 is a price, a missing price is a field error (FE Q4)")
    void confirm_missingPriceIsAFieldError_explicitZeroIsAccepted() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 2);
        order.getLines().get(0).setUnitPrice(BigDecimal.ZERO);
        order.getLines().get(0).setCreatedAt(Instant.parse("2026-10-14T01:00:00Z"));
        order.getLines().get(1).setCreatedAt(Instant.parse("2026-10-14T02:00:00Z"));

        assertThatThrownBy(() -> service.confirm(order.getPurchaseOrderId(), new PurchaseOrderVersionRequest(2L), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[1].unitPrice"));
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("G7a: lines saved in one flush share created_at — the id breaks the tie, so reads and field indexes agree")
    void linesWithTheSameCreatedAt_areOrderedById_andConfirmIndexesThatOrder() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 2);
        Instant sameMicro = Instant.parse("2026-10-14T01:00:00.123456Z");
        PurchaseOrderLine high = order.getLines().get(0);
        PurchaseOrderLine low = order.getLines().get(1);
        high.setPurchaseOrderLineId(UUID.fromString("ffffffff-0000-0000-0000-000000000000"));
        low.setPurchaseOrderLineId(UUID.fromString("00000000-0000-0000-0000-00000000000f"));
        high.setCreatedAt(sameMicro);
        low.setCreatedAt(sameMicro);
        high.setUnitPrice(BigDecimal.ONE);   // the high id has a price, the low id has none

        assertThat(service.get(order.getPurchaseOrderId()).lines())
                .extracting(PurchaseOrderLineResponse::purchaseOrderLineId)
                .containsExactly(low.getPurchaseOrderLineId(), high.getPurchaseOrderLineId());
        assertThatThrownBy(() -> service.confirm(order.getPurchaseOrderId(), new PurchaseOrderVersionRequest(2L), null))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsOnlyKeys("lines[0].unitPrice"));
    }

    @Test
    @DisplayName("G7a confirm: DRAFT → CONFIRMED, who and when recorded, history has the confirm step")
    void confirm_recordsActorAndTime() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.getLines().get(0).setUnitPrice(new BigDecimal("10"));
        UUID actor = UUID.randomUUID();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actor));
        when(userLookupService.findUsernames(anyCollection())).thenReturn(Map.of(actor, "buyer"));

        PurchaseOrderResponse response = service.confirm(order.getPurchaseOrderId(), new PurchaseOrderVersionRequest(2L), null);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(order.getConfirmedBy()).isEqualTo(actor);
        assertThat(response.history()).extracting(PurchaseOrderHistoryResponse::action).containsExactly("CREATE", "CONFIRM");
        assertThat(response.history().get(1).actorUsername()).isEqualTo("buyer");
    }

    @Test
    @DisplayName("G7a confirm: a non-DRAFT order is STATE_CONFLICT")
    void confirm_notDraft_isStateConflict() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.setStatus(PurchaseOrderStatus.CONFIRMED);

        assertThatThrownBy(() -> service.confirm(order.getPurchaseOrderId(), new PurchaseOrderVersionRequest(2L), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    @DisplayName("G7a cancel with a body: a CONFIRMED order with nothing received is cancelled and its quota released")
    void cancel_confirmedOrderNothingReceived_releasesQuota() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.setStatus(PurchaseOrderStatus.CONFIRMED);
        UUID actor = UUID.randomUUID();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actor));

        PurchaseOrderResponse response = service.cancel(order.getPurchaseOrderId(),
                new PurchaseOrderReasonRequest(2L, " Supplier cannot deliver "), null);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.reason()).isEqualTo("Supplier cannot deliver");
        assertThat(order.getPreviousStatus()).isEqualTo(PurchaseOrderStatus.CONFIRMED);
        verify(planningLinkService).releasePurchaseOrder(eq(order.getPurchaseOrderId()), eq("Supplier cannot deliver"),
                any(Instant.class), eq(actor));
    }

    @Test
    @DisplayName("G7a cancel with a body: once something was received the order is closed, not cancelled")
    void cancel_somethingReceived_isStateConflictAndReleasesNothing() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        order.getLines().get(0).setReceivedQuantity(new BigDecimal("4"));

        assertThatThrownBy(() -> service.cancel(order.getPurchaseOrderId(), new PurchaseOrderReasonRequest(2L, "x"), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(planningLinkService);
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("G7a cancel without a body (legacy): DRAFT cancelled, quota still released")
    void cancel_legacyNoBody_releasesQuotaToo() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);

        service.cancel(order.getPurchaseOrderId(), null, null);

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        assertThat(order.getStatusReason()).isNull();
        verify(planningLinkService).releasePurchaseOrder(eq(order.getPurchaseOrderId()), eq("Purchase order cancelled"),
                any(Instant.class), any());
    }

    @Test
    @DisplayName("G7a cancel replay: the same key returns the current state and releases nothing again")
    void cancel_replay_doesNotReleaseTwice() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        PurchaseOrderReasonRequest request = new PurchaseOrderReasonRequest(2L, "x");
        when(idempotencyRecordRepository.findByIdempotencyKeyAndCommand("k", PurchasingCommand.PURCHASE_ORDER_CANCEL))
                .thenReturn(Optional.of(PurchasingIdempotencyRecord.builder()
                        .resourceId(order.getPurchaseOrderId())
                        .payloadHash(idempotencySupport.payloadHash(
                                new PurchasingIdempotency.Fingerprint(order.getPurchaseOrderId(), request)))
                        .build()));

        PurchaseOrderResponse response = service.cancel(order.getPurchaseOrderId(), request, "k");

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(planningLinkService, never()).releasePurchaseOrder(any(), any(), any(), any());
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }

    @Test
    @DisplayName("G7a close: the open remainder becomes closedQuantity, received stays, quota is NOT released (FE Q13)")
    void close_partiallyReceived_closesTheRemainder() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        PurchaseOrderLine line = order.getLines().get(0);
        line.setOrderedQuantity(new BigDecimal("40"));
        line.setReceivedQuantity(new BigDecimal("25"));

        PurchaseOrderResponse response = service.close(order.getPurchaseOrderId(),
                new PurchaseOrderReasonRequest(2L, "Rest not needed"), null);

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(line.getClosedQuantity()).isEqualByComparingTo("15");
        assertThat(line.getReceivedQuantity()).isEqualByComparingTo("25");
        assertThat(line.openQuantity()).isEqualByComparingTo("0");
        assertThat(response.lines().get(0).closedQuantity()).isEqualByComparingTo("15");
        assertThat(order.getPreviousStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(response.history()).extracting(PurchaseOrderHistoryResponse::fromStatus).contains("PARTIALLY_RECEIVED");
        verify(planningLinkService, never()).releasePurchaseOrder(any(), any(), any(), any());
    }

    @Test
    @DisplayName("G7a close: a fully received order has nothing open — STATE_CONFLICT")
    void close_fullyReceived_isStateConflict() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        order.setStatus(PurchaseOrderStatus.FULLY_RECEIVED);
        order.getLines().get(0).setReceivedQuantity(new BigDecimal("10"));

        assertThatThrownBy(() -> service.close(order.getPurchaseOrderId(), new PurchaseOrderReasonRequest(2L, "x"), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    // ── G7a: reads ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("G7a get: planning sources on the line; a held source makes the order sourceLocked")
    void get_mapsSourcesAndSourceLocked() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        UUID lineId = order.getLines().get(0).getPurchaseOrderLineId();
        UUID runId = UUID.randomUUID();
        when(planningLinkService.findSources(anyCollection())).thenReturn(Map.of(lineId, List.of(
                new PurchaseOrderPlanningSource(lineId, UUID.randomUUID(), runId, "RUN-1", UUID.randomUUID(),
                        UUID.randomUUID(), "abc", new BigDecimal("10"), Instant.now(), null, null))));

        PurchaseOrderResponse response = service.get(order.getPurchaseOrderId());

        assertThat(response.sourceLocked()).isTrue();
        assertThat(response.lines().get(0).sources()).singleElement()
                .satisfies(source -> {
                    assertThat(source.runId()).isEqualTo(runId);
                    assertThat(source.runCode()).isEqualTo("RUN-1");
                    assertThat(source.sourceVersion()).isEqualTo("abc");
                });
    }

    @Test
    @DisplayName("G7a get: only released sources ⇒ not sourceLocked (the cancel gave the quota back)")
    void get_onlyReleasedSources_isNotLocked() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder order = stubbedDraft(f, 1);
        UUID lineId = order.getLines().get(0).getPurchaseOrderLineId();
        when(planningLinkService.findSources(anyCollection())).thenReturn(Map.of(lineId, List.of(
                new PurchaseOrderPlanningSource(lineId, UUID.randomUUID(), UUID.randomUUID(), "RUN-1", UUID.randomUUID(),
                        UUID.randomUUID(), "abc", new BigDecimal("10"), Instant.now(), Instant.now(), "cancelled"))));

        assertThat(service.get(order.getPurchaseOrderId()).sourceLocked()).isFalse();
    }

    @Test
    @DisplayName("G7a list: sourceLocked for the whole page in one query (C14), search and dates forwarded")
    void list_resolvesSourceLockedInOneQuery() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);
        PurchaseOrder first = stubbedDraft(f, 1);
        PurchaseOrder second = stubbedDraft(f, 1);
        LocalDate from = LocalDate.of(2026, 10, 1);
        when(purchaseOrderRepository.search(eq(f.company().getCompanyId()), eq(f.plant().getPlantId()), eq(null), eq(null),
                eq(null), eq("acme"), eq(from), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(planningLinkService.findSourceLockedOrderIds(anyCollection())).thenReturn(Set.of(second.getPurchaseOrderId()));

        var page = service.list(f.company().getCompanyId(), f.plant().getPlantId(), null, null, null,
                " acme ", from, null, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(PurchaseOrderResponse::sourceLocked).containsExactly(false, true);
        verify(planningLinkService, times(1)).findSourceLockedOrderIds(anyCollection());
    }

    @Test
    @DisplayName("G7a list: orderDateFrom after orderDateTo is a field error, no query")
    void list_invertedDateRange_isAFieldError() {
        DraftFixture f = draftFixture(ItemType.RAW_MATERIAL);

        assertThatThrownBy(() -> service.list(f.company().getCompanyId(), f.plant().getPlantId(), null, null, null,
                null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), PageRequest.of(0, 20)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("orderDateFrom"));
        verify(purchaseOrderRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }


    private PurchaseOrder purchaseOrder(PurchaseOrderStatus status) {
        Company company = company(UUID.randomUUID());
        Plant plant = plant(UUID.randomUUID(), company);
        Warehouse warehouse = warehouse(UUID.randomUUID(), plant);
        Supplier supplier = supplier(UUID.randomUUID(), company);
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseOrderId(UUID.randomUUID())
                .company(company)
                .plant(plant)
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrderNo("PO-001")
                .status(status)
                .orderDate(LocalDate.now())
                .expectedDate(LocalDate.now().plusDays(7))
                .lines(new ArrayList<>())
                .build();
        order.getLines().add(PurchaseOrderLine.builder()
                .purchaseOrderLineId(UUID.randomUUID())
                .purchaseOrder(order)
                .item(item(UUID.randomUUID(), company))
                .orderedQuantity(new BigDecimal("10"))
                .receivedQuantity(BigDecimal.ZERO)
                .expectedDate(order.getExpectedDate())
                .build());
        return order;
    }

    private Item item(UUID itemId, Company company) {
        return item(itemId, company, ItemType.RAW_MATERIAL);
    }

    private Item item(UUID itemId, Company company, ItemType type) {
        return Item.builder()
                .itemId(itemId)
                .company(company)
                .code("ITEM")
                .name("Item")
                .type(type)
                .unit("EA")
                .status(ItemStatus.ACTIVE)
                .build();
    }

    private Supplier supplier(UUID supplierId, Company company) {
        return Supplier.builder()
                .supplierId(supplierId)
                .company(company)
                .code("SUP")
                .name("Supplier")
                .status(SupplierStatus.ACTIVE)
                .build();
    }

    private Warehouse warehouse(UUID warehouseId, Plant plant) {
        return Warehouse.builder()
                .warehouseId(warehouseId)
                .plant(plant)
                .code("WH1")
                .name("Warehouse 1")
                .type(WarehouseType.GENERAL)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Plant plant(UUID plantId, Company company) {
        return Plant.builder()
                .plantId(plantId)
                .company(company)
                .code("P1")
                .name("Plant 1")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private Company company(UUID companyId) {
        return Company.builder()
                .companyId(companyId)
                .code("ACME")
                .name("ACME")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
