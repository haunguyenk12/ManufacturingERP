package com.erp.manufacturing.module.bom.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.dto.BomCreateRequest;
import com.erp.manufacturing.module.bom.dto.BomLineCreateRequest;
import com.erp.manufacturing.module.bom.dto.BomLineUpdateRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BomService tests")
class BomServiceTest {

    @Mock BomHeaderRepository bomHeaderRepository;
    @Mock BomLineRepository bomLineRepository;
    @Mock ItemLookupService itemLookupService;

    BomService service;

    @BeforeEach
    void setUp() {
        service = new BomService(bomHeaderRepository, bomLineRepository, itemLookupService, new BomMapper());
    }

    @Test
    void createBom_success_normalizesRevisionAndCreatesDraft() {
        UUID companyId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();
        Item parent = item(parentItemId, companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE);
        when(itemLookupService.getActiveItem(parentItemId)).thenReturn(parent);
        when(bomHeaderRepository.existsByCompanyCompanyIdAndParentItemItemIdAndRevision(companyId, parentItemId, "R1"))
                .thenReturn(false);
        when(bomHeaderRepository.save(any(BomHeader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createBom(companyId, new BomCreateRequest(parentItemId, "r1", " Main formula "));

        ArgumentCaptor<BomHeader> captor = ArgumentCaptor.forClass(BomHeader.class);
        verify(bomHeaderRepository).save(captor.capture());
        assertThat(captor.getValue().getRevision()).isEqualTo("R1");
        assertThat(captor.getValue().getStatus()).isEqualTo(BomStatus.DRAFT);
        assertThat(captor.getValue().getDescription()).isEqualTo("Main formula");
    }

    @Test
    void createBom_parentWrongType_fails() {
        UUID companyId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(parentItemId))
                .thenReturn(item(parentItemId, companyId, "RM-001", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE));

        assertThatThrownBy(() -> service.createBom(companyId, new BomCreateRequest(parentItemId, "R1", null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void addLine_componentDifferentCompany_fails() {
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BomHeader bom = draftBom(bomId, item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE));
        when(bomHeaderRepository.findWithLinesByBomId(bomId)).thenReturn(Optional.of(bom));
        when(itemLookupService.getActiveItem(componentId))
                .thenReturn(item(componentId, otherCompanyId, "RM-001", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE));

        assertThatThrownBy(() -> service.addLine(bomId, lineCreate(componentId, 10)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void addLine_serviceComponent_fails() {
        UUID companyId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BomHeader bom = draftBom(bomId, item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE));
        when(bomHeaderRepository.findWithLinesByBomId(bomId)).thenReturn(Optional.of(bom));
        when(itemLookupService.getActiveItem(componentId))
                .thenReturn(item(componentId, companyId, "SVC-001", ItemType.SERVICE, ItemStatus.ACTIVE));

        assertThatThrownBy(() -> service.addLine(bomId, lineCreate(componentId, 10)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));
    }

    @Test
    void addLine_duplicateComponent_fails() {
        UUID companyId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BomHeader bom = draftBom(bomId, item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE));
        Item component = item(componentId, companyId, "RM-001", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE);
        when(bomHeaderRepository.findWithLinesByBomId(bomId)).thenReturn(Optional.of(bom));
        when(itemLookupService.getActiveItem(componentId)).thenReturn(component);
        when(bomLineRepository.existsByBomBomIdAndComponentItemItemId(bomId, componentId)).thenReturn(true);

        assertThatThrownBy(() -> service.addLine(bomId, lineCreate(componentId, 10)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));
    }

    @Test
    void addLine_invalidQuantityOrScrapRate_fails() {
        UUID companyId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BomHeader bom = draftBom(bomId, item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE));
        Item component = item(componentId, companyId, "RM-001", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE);
        when(bomHeaderRepository.findWithLinesByBomId(bomId)).thenReturn(Optional.of(bom));
        when(itemLookupService.getActiveItem(componentId)).thenReturn(component);

        assertThatThrownBy(() -> service.addLine(bomId, new BomLineCreateRequest(
                componentId, 10, BigDecimal.ZERO, BigDecimal.ZERO)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION));

        assertThatThrownBy(() -> service.addLine(bomId, new BomLineCreateRequest(
                componentId, 10, BigDecimal.ONE, BigDecimal.ONE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void updateLine_nonDraftBom_fails() {
        UUID companyId = UUID.randomUUID();
        UUID bomId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        BomHeader bom = activeBom(bomId, item(UUID.randomUUID(), companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE));
        BomLine line = line(lineId, bom, item(componentId, companyId, "RM-001", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE), 10);
        when(bomLineRepository.findById(lineId)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> service.updateLine(lineId, new BomLineUpdateRequest(
                componentId, 10, BigDecimal.ONE, BigDecimal.ZERO)))
                .isInstanceOf(AppException.class)
                // D7: BOM status conflict is 409 STATE_CONFLICT, not 422 OPERATION_NOT_ALLOWED
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));
    }

    @Test
    void activateBom_success_deactivatesPreviousActiveRevision() {
        UUID companyId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID oldActiveId = UUID.randomUUID();
        Item parent = item(parentItemId, companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE);
        Item component = item(componentId, companyId, "RM-001", ItemType.RAW_MATERIAL, ItemStatus.ACTIVE);
        BomHeader candidate = draftBom(candidateId, parent);
        candidate.getLines().add(line(UUID.randomUUID(), candidate, component, 10));
        BomHeader oldActive = activeBom(oldActiveId, parent);

        when(bomHeaderRepository.findWithLinesByBomId(candidateId)).thenReturn(Optional.of(candidate));
        when(bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
                companyId, componentId, BomStatus.ACTIVE)).thenReturn(Optional.empty());
        when(bomHeaderRepository.findByCompanyCompanyIdAndParentItemItemIdAndStatus(
                companyId, parentItemId, BomStatus.ACTIVE)).thenReturn(Optional.of(oldActive));
        when(bomHeaderRepository.save(candidate)).thenReturn(candidate);

        service.activateBom(candidateId);

        assertThat(oldActive.getStatus()).isEqualTo(BomStatus.INACTIVE);
        assertThat(candidate.getStatus()).isEqualTo(BomStatus.ACTIVE);
        verify(bomHeaderRepository).saveAndFlush(oldActive);
        verify(bomHeaderRepository).save(candidate);
    }

    @Test
    void activateBom_circularReference_fails() {
        UUID companyId = UUID.randomUUID();
        UUID parentItemId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        Item parent = item(parentItemId, companyId, "FG-100", ItemType.FINISHED_GOOD, ItemStatus.ACTIVE);
        Item component = item(componentId, companyId, "SUB-100", ItemType.WIP, ItemStatus.ACTIVE);
        BomHeader candidate = draftBom(candidateId, parent);
        candidate.getLines().add(line(UUID.randomUUID(), candidate, component, 10));

        BomHeader childActive = activeBom(UUID.randomUUID(), component);
        childActive.getLines().add(line(UUID.randomUUID(), childActive, parent, 10));

        when(bomHeaderRepository.findWithLinesByBomId(candidateId)).thenReturn(Optional.of(candidate));
        when(bomHeaderRepository.findWithLinesByCompanyCompanyIdAndParentItemItemIdAndStatus(
                companyId, componentId, BomStatus.ACTIVE)).thenReturn(Optional.of(childActive));

        assertThatThrownBy(() -> service.activateBom(candidateId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BOM_CIRCULAR_REFERENCE));

        verify(bomHeaderRepository, never()).save(any());
    }

    private BomLineCreateRequest lineCreate(UUID componentId, int lineNo) {
        return new BomLineCreateRequest(componentId, lineNo, BigDecimal.ONE, BigDecimal.ZERO);
    }

    private BomHeader draftBom(UUID bomId, Item parent) {
        return bom(bomId, parent, BomStatus.DRAFT);
    }

    private BomHeader activeBom(UUID bomId, Item parent) {
        return bom(bomId, parent, BomStatus.ACTIVE);
    }

    private BomHeader bom(UUID bomId, Item parent, BomStatus status) {
        return BomHeader.builder()
                .bomId(bomId)
                .company(parent.getCompany())
                .parentItem(parent)
                .revision("R1")
                .status(status)
                .lines(new ArrayList<>())
                .build();
    }

    private BomLine line(UUID lineId, BomHeader bom, Item component, int lineNo) {
        return BomLine.builder()
                .lineId(lineId)
                .bom(bom)
                .componentItem(component)
                .lineNo(lineNo)
                .quantityPer(BigDecimal.ONE)
                .scrapRate(BigDecimal.ZERO)
                .build();
    }

    private Item item(UUID itemId, UUID companyId, String code, ItemType type, ItemStatus status) {
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
                .lotTracked(false)
                .status(status)
                .build();
    }
}
