package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.dto.BomCopyRequest;
import com.erp.manufacturing.module.bom.dto.BomDraftLineRequest;
import com.erp.manufacturing.module.bom.dto.BomDraftSaveRequest;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import com.erp.manufacturing.module.bom.mapper.BomMapper;
import com.erp.manufacturing.module.bom.repository.BomHeaderRepository;
import com.erp.manufacturing.module.bom.repository.BomLineRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** G5: atomic draft save, copy, versioned deactivate and the new activation gates of {@link BomService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("BomService – G5 drafts, copy and activation gates")
class BomDraftServiceTest {

    @Mock BomHeaderRepository bomHeaderRepository;
    @Mock BomLineRepository bomLineRepository;
    @Mock ItemLookupService itemLookupService;

    BomService service;

    final UUID companyId = UUID.randomUUID();
    Item product;
    Item raw;
    Item wip;

    @BeforeEach
    void setUp() {
        service = new BomService(bomHeaderRepository, bomLineRepository, itemLookupService, new BomMapper());
        product = item("FG-DEMO", ItemType.FINISHED_GOOD, "PCS");
        raw = item("RM-DEMO", ItemType.RAW_MATERIAL, "KG");
        wip = item("WIP-DEMO", ItemType.WIP, "PCS");
    }

    /** FE A01: output 10, component 25, scrap 0.05 ⇒ one DRAFT, quantityPer 2.5, componentQuantity 25 back. */
    @Test
    void saveDraft_create_storesQuantityPerAndReturnsTheBatchFigureTyped() {
        stubActive(product, raw);
        when(bomHeaderRepository.findMaxVersionNumber(companyId, product.getItemId())).thenReturn(0);
        when(bomHeaderRepository.saveAndFlush(any(BomHeader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BomResponse response = service.saveDraft(companyId, request(null, null, " bom-demo-01 ", "10",
                line(1, raw, "25", "0.05")));

        ArgumentCaptor<BomHeader> saved = ArgumentCaptor.forClass(BomHeader.class);
        verify(bomHeaderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getBomCode()).isEqualTo("BOM-DEMO-01");
        assertThat(saved.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(saved.getValue().getRevision()).isEqualTo("1");
        assertThat(saved.getValue().getStatus()).isEqualTo(BomStatus.DRAFT);
        assertThat(saved.getValue().getLines().get(0).getQuantityPer()).isEqualByComparingTo("2.5");
        assertThat(response.outputQuantity()).isEqualByComparingTo("10");
        assertThat(response.outputUomCode()).isEqualTo("PCS");
        assertThat(response.lines().get(0).componentQuantity()).isEqualByComparingTo("25");
        assertThat(response.lines().get(0).componentUomCode()).isEqualTo("KG");
        assertThat(response.lines().get(0).scrapRate()).isEqualByComparingTo("0.05");
        // The version number is taken under the product's lock.
        InOrder order = inOrder(itemLookupService, bomHeaderRepository);
        order.verify(itemLookupService).lockItem(product.getItemId());
        order.verify(bomHeaderRepository).findMaxVersionNumber(companyId, product.getItemId());
    }

    @Test
    void saveDraft_create_numbersTheNextVersionOfTheProduct() {
        stubActive(product, raw);
        when(bomHeaderRepository.findMaxVersionNumber(companyId, product.getItemId())).thenReturn(3);
        when(bomHeaderRepository.saveAndFlush(any(BomHeader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BomResponse response = service.saveDraft(companyId, request(null, null, "BOM-4", "1", line(1, raw, "2", "0")));

        assertThat(response.versionNumber()).isEqualTo(4);
        assertThat(response.revision()).isEqualTo("4");
    }

    /** FE Q2: a quotient that needs more than 6 decimals is refused on the line, never rounded. */
    @Test
    void saveDraft_quotientNeedingMoreThanSixDecimals_isRefusedOnTheLineBeforeAnyWrite() {
        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null, "BOM-X", "3",
                line(1, raw, "1", "0"))))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsKey("lines[0].componentQuantity"));
        verifyNoInteractions(bomHeaderRepository);
    }

    @Test
    void saveDraft_duplicateComponentAndProductAsComponent_areFieldErrors() {
        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null, "BOM-X", "1",
                line(1, raw, "1", "0"), line(2, raw, "1", "0"), line(3, product, "1", "0"))))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsKeys("lines[1].componentItemId", "lines[2].componentItemId"));
        verifyNoInteractions(bomHeaderRepository);
    }

    /** FE A03: a finished good is not a component on the new paths. */
    @Test
    void saveDraft_finishedGoodComponent_isRefused() {
        Item otherProduct = item("FG-OTHER", ItemType.FINISHED_GOOD, "PCS");
        stubActive(product, otherProduct);

        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null, "BOM-X", "1",
                line(1, otherProduct, "1", "0"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
        verify(bomHeaderRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveDraft_bomCodeTaken_isAConflictAndNothingIsSaved() {
        stubActive(product, raw);
        when(bomHeaderRepository.existsByCompanyCompanyIdAndBomCodeIgnoreCase(companyId, "BOM-TAKEN")).thenReturn(true);

        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null, "bom-taken", "1",
                line(1, raw, "1", "0"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
        verify(bomHeaderRepository, never()).saveAndFlush(any());
    }

    /** FE A05: a stale expectedVersion is refused before a single line is touched. */
    @Test
    void saveDraft_replaceWithStaleVersion_isAConcurrentModificationBeforeAnyWrite() {
        BomHeader draft = draft(raw, "2");
        draft.setVersion(5L);
        when(bomHeaderRepository.findWithLinesByBomId(draft.getBomId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.saveDraft(companyId, request(draft.getBomId(), 4L, "BOM-1", "1",
                line(1, raw, "3", "0"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        assertThat(draft.getLines().get(0).getQuantityPer()).isEqualByComparingTo("2");
        verify(bomHeaderRepository, never()).flush();
        verify(bomHeaderRepository, never()).saveAndFlush(any());
    }

    /** FE A06: only a DRAFT is edited in place; ACTIVE is copied. */
    @Test
    void saveDraft_replaceOfAnActiveBom_isAStateConflict() {
        BomHeader active = draft(raw, "2");
        active.setStatus(BomStatus.ACTIVE);
        active.setVersion(0L);
        when(bomHeaderRepository.findWithLinesByBomId(active.getBomId())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.saveDraft(companyId, request(active.getBomId(), 0L, "BOM-1", "1",
                line(1, raw, "3", "0"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    /** Another company's BOM answers like a missing one — the id is never confirmed. */
    @Test
    void saveDraft_replaceOfAnotherCompanysBom_isNotFound() {
        BomHeader draft = draft(raw, "2");
        when(bomHeaderRepository.findWithLinesByBomId(draft.getBomId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.saveDraft(UUID.randomUUID(), request(draft.getBomId(), 0L, "BOM-1", "1",
                line(1, raw, "3", "0"))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    /** A component that stays keeps its line (and id); a removed one goes; the version moves once. */
    @Test
    void saveDraft_replace_keepsTheLineOfAComponentThatStaysAndDropsTheRest() {
        BomHeader draft = draft(raw, "2");
        BomLine kept = draft.getLines().get(0);
        draft.getLines().add(BomLine.builder().lineId(UUID.randomUUID()).bom(draft).componentItem(wip)
                .lineNo(2).quantityPer(BigDecimal.ONE).scrapRate(BigDecimal.ZERO).build());
        draft.setVersion(0L);
        when(bomHeaderRepository.findWithLinesByBomId(draft.getBomId())).thenReturn(Optional.of(draft));
        stubActive(raw);
        when(bomHeaderRepository.saveAndFlush(any(BomHeader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BomResponse response = service.saveDraft(companyId, request(draft.getBomId(), 0L, "BOM-1", "4",
                line(5, raw, "12", "0.1")));

        assertThat(draft.getLines()).containsExactly(kept);
        assertThat(kept.getLineNo()).isEqualTo(5);
        assertThat(kept.getQuantityPer()).isEqualByComparingTo("3");
        assertThat(response.outputQuantity()).isEqualByComparingTo("4");
        assertThat(response.lines().get(0).lineId()).isEqualTo(kept.getLineId());
        // removed lines flushed, then survivors parked, before the final numbers are written
        verify(bomHeaderRepository, times(2)).flush();
    }

    @Test
    void activate_futureEffectiveDate_isRefusedAndNothingChanges() {
        BomHeader draft = draft(raw, "1");
        draft.setEffectiveFrom(Instant.now().plus(1, ChronoUnit.DAYS));
        stubActivationRead(draft);

        assertThatThrownBy(() -> service.activateBom(draft.getBomId(), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.EFFECTIVE_DATE_FUTURE));
        assertThat(draft.getStatus()).isEqualTo(BomStatus.DRAFT);
        verify(bomHeaderRepository, never()).saveAndFlush(any());
    }

    /** FE R03: a draft saved last month may point at a component deactivated since. */
    @Test
    void activate_inactiveComponent_isRefused() {
        Item retired = item("RM-OLD", ItemType.RAW_MATERIAL, "KG");
        retired.setStatus(ItemStatus.INACTIVE);
        BomHeader draft = draft(retired, "1");
        stubActivationRead(draft);

        assertThatThrownBy(() -> service.activateBom(draft.getBomId(), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));
        verify(bomHeaderRepository, never()).saveAndFlush(any());
    }

    @Test
    void activate_staleExpectedVersion_isRefused() {
        BomHeader draft = draft(raw, "1");
        draft.setVersion(2L);
        stubActivationRead(draft);

        assertThatThrownBy(() -> service.activateBom(draft.getBomId(), 1L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
    }

    /** FE R04: copy makes a new DRAFT version with the same lines; the source is untouched. */
    @Test
    void copy_createsTheNextDraftVersionWithTheSameLines_andLeavesTheSourceAlone() {
        BomHeader source = draft(raw, "2.5");
        source.setStatus(BomStatus.ACTIVE);
        source.setOutputQuantity(new BigDecimal("10"));
        source.setDescription("source notes");
        when(bomHeaderRepository.findParentItemIdByBomId(source.getBomId())).thenReturn(Optional.of(product.getItemId()));
        when(bomHeaderRepository.findWithLinesByBomId(source.getBomId())).thenReturn(Optional.of(source));
        when(bomHeaderRepository.findMaxVersionNumber(companyId, product.getItemId())).thenReturn(2);
        when(bomHeaderRepository.saveAndFlush(any(BomHeader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BomResponse copy = service.copyBom(source.getBomId(), new BomCopyRequest("bom-copy", null, null, null));

        assertThat(copy.status()).isEqualTo("DRAFT");
        assertThat(copy.bomCode()).isEqualTo("BOM-COPY");
        assertThat(copy.versionNumber()).isEqualTo(3);
        assertThat(copy.description()).isEqualTo("source notes");
        assertThat(copy.lines()).hasSize(1);
        assertThat(copy.lines().get(0).componentQuantity()).isEqualByComparingTo("25");
        assertThat(source.getStatus()).isEqualTo(BomStatus.ACTIVE);
        verify(itemLookupService).lockItem(product.getItemId());
    }

    @Test
    void deactivate_withStaleVersion_isRefusedAndTheBomStaysActive() {
        BomHeader active = draft(raw, "1");
        active.setStatus(BomStatus.ACTIVE);
        active.setVersion(3L);
        when(bomHeaderRepository.findWithLinesByBomId(active.getBomId())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.deactivateBom(active.getBomId(), 2L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.CONCURRENT_MODIFICATION));
        assertThat(active.getStatus()).isEqualTo(BomStatus.ACTIVE);
    }

    private void stubActivationRead(BomHeader draft) {
        when(bomHeaderRepository.findParentItemIdByBomId(draft.getBomId())).thenReturn(Optional.of(product.getItemId()));
        when(bomHeaderRepository.findWithLinesByBomId(draft.getBomId())).thenReturn(Optional.of(draft));
    }

    private void stubActive(Item... items) {
        for (Item item : items) {
            when(itemLookupService.getActiveItem(item.getItemId())).thenReturn(item);
        }
    }

    private BomDraftSaveRequest request(UUID bomId, Long expectedVersion, String code, String output,
                                        BomDraftLineRequest... lines) {
        return new BomDraftSaveRequest(bomId, expectedVersion, code, product.getItemId(), null,
                new BigDecimal(output), null, null, List.of(lines));
    }

    private BomDraftLineRequest line(int lineNo, Item component, String quantity, String scrapRate) {
        return new BomDraftLineRequest(lineNo, component.getItemId(), new BigDecimal(quantity), new BigDecimal(scrapRate));
    }

    private BomHeader draft(Item component, String quantityPer) {
        BomHeader bom = BomHeader.builder()
                .bomId(UUID.randomUUID())
                .company(product.getCompany())
                .parentItem(product)
                .revision("1")
                .bomCode("BOM-1")
                .versionNumber(1)
                .status(BomStatus.DRAFT)
                .lines(new ArrayList<>())
                .build();
        bom.getLines().add(BomLine.builder().lineId(UUID.randomUUID()).bom(bom).componentItem(component)
                .lineNo(1).quantityPer(new BigDecimal(quantityPer)).scrapRate(BigDecimal.ZERO).build());
        return bom;
    }

    private Item item(String code, ItemType type, String unit) {
        return Item.builder()
                .itemId(UUID.randomUUID())
                .company(Company.builder().companyId(companyId).code("ACME").name("ACME")
                        .status(OrganizationStatus.ACTIVE).build())
                .code(code).name(code).type(type).unit(unit).lotTracked(false)
                .status(ItemStatus.ACTIVE)
                .build();
    }
}
