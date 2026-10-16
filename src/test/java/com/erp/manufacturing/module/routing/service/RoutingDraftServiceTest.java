package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.inventory.domain.ItemStatus;
import com.erp.manufacturing.module.inventory.domain.ItemType;
import com.erp.manufacturing.module.inventory.service.ItemLookupService;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.dto.RoutingCopyRequest;
import com.erp.manufacturing.module.routing.dto.RoutingDraftOperationRequest;
import com.erp.manufacturing.module.routing.dto.RoutingDraftSaveRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.mapper.RoutingMapper;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/** G5: atomic graph save, predecessors, copy and the new activation gate of {@link RoutingService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingService – G5 drafts, predecessors and copy")
class RoutingDraftServiceTest {

    @Mock RoutingHeaderRepository routingHeaderRepository;
    @Mock ItemLookupService itemLookupService;
    @Mock WorkCenterLookupService workCenterLookupService;

    RoutingService service;

    final UUID companyId = UUID.randomUUID();
    final UUID plantId = UUID.randomUUID();
    Company company;
    Item product;
    WorkCenter assembly;
    WorkCenter paint;

    @BeforeEach
    void setUp() {
        service = new RoutingService(routingHeaderRepository, itemLookupService, workCenterLookupService, new RoutingMapper());
        company = Company.builder().companyId(companyId).code("ACME").name("ACME").status(OrganizationStatus.ACTIVE).build();
        product = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-DEMO").name("Demo")
                .type(ItemType.FINISHED_GOOD).unit("PCS").status(ItemStatus.ACTIVE).build();
        assembly = workCenter("WC-ASM");
        paint = workCenter("WC-PNT");
    }

    @Test
    void saveDraft_create_persistsTheWholeGraphWithPredecessors() {
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        stubWorkCenters();
        when(routingHeaderRepository.saveAndFlush(any(RoutingHeader.class))).thenAnswer(withIds());

        RoutingResponse response = service.saveDraft(companyId, request(null, null,
                op(null, "assembly", 10, assembly, List.of()),
                op(null, "paint", 20, paint, List.of("assembly"))));

        ArgumentCaptor<RoutingHeader> saved = ArgumentCaptor.forClass(RoutingHeader.class);
        verify(routingHeaderRepository).saveAndFlush(saved.capture());
        RoutingOperation paintOperation = saved.getValue().getOperations().stream()
                .filter(operation -> operation.getSequence() == 20).findFirst().orElseThrow();
        assertThat(paintOperation.getPredecessors()).extracting(RoutingOperation::getSequence).containsExactly(10);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.version()).isEqualTo("1");
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.plantId()).isEqualTo(plantId);
        assertThat(response.totalSetupMinutes()).isEqualByComparingTo("60");
        assertThat(response.totalRunMinutesPerUnit()).isEqualByComparingTo("4");
        assertThat(response.operations().get(1).predecessorOperationIds())
                .containsExactly(response.operations().get(0).routingOperationId());
        verify(itemLookupService).lockItem(product.getItemId());
    }

    /** FE A25: every graph error comes back by field and nothing is read or written. */
    @Test
    void saveDraft_predecessorWithALargerSequenceOrUnknownKey_isRefusedByField() {
        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null,
                op(null, "assembly", 10, assembly, List.of("paint")),
                op(null, "paint", 20, paint, List.of("ghost")))))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsKeys("operations[0].predecessorClientKeys", "operations[1].predecessorClientKeys"));
        verifyNoInteractions(routingHeaderRepository, workCenterLookupService);
    }

    @Test
    void saveDraft_selfPredecessorAndDuplicateSequence_areRefused() {
        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null,
                op(null, "assembly", 10, assembly, List.of("assembly")),
                op(null, "paint", 10, paint, List.of()))))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors())
                        .containsKeys("operations[0].predecessorClientKeys", "operations[1].sequence"));
    }

    /** FE Q18: one open draft per product. */
    @Test
    void saveDraft_createWhileTheProductHasAnOpenDraft_isRefused() {
        when(itemLookupService.getActiveItem(product.getItemId())).thenReturn(product);
        stubWorkCenters();
        when(routingHeaderRepository.existsByCompanyCompanyIdAndItemItemIdAndStatus(
                companyId, product.getItemId(), RoutingStatus.DRAFT)).thenReturn(true);

        assertThatThrownBy(() -> service.saveDraft(companyId, request(null, null,
                op(null, "assembly", 10, assembly, List.of()))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPEN_DRAFT_EXISTS));
        verify(routingHeaderRepository, never()).saveAndFlush(any());
    }

    /** Re-save: an operation named by id keeps it, swaps sequence safely and the removed one goes. */
    @Test
    void saveDraft_replace_keepsNamedOperationsAndParksThemBeforeRenumbering() {
        RoutingHeader draft = draft();
        RoutingOperation first = draft.getOperations().get(0);
        RoutingOperation second = draft.getOperations().get(1);
        when(routingHeaderRepository.findWithOperationsByRoutingId(draft.getRoutingId())).thenReturn(Optional.of(draft));
        stubWorkCenters();
        when(routingHeaderRepository.saveAndFlush(any(RoutingHeader.class))).thenAnswer(withIds());

        service.saveDraft(companyId, request(draft.getRoutingId(), 0L,
                op(second.getRoutingOperationId(), "b", 10, paint, List.of()),
                op(first.getRoutingOperationId(), "a", 20, assembly, List.of("b"))));

        assertThat(second.getSequence()).isEqualTo(10);
        assertThat(first.getSequence()).isEqualTo(20);
        assertThat(first.getPredecessors()).containsExactly(second);
        // removed operations flushed, then survivors parked above any real sequence, then renumbered
        verify(routingHeaderRepository, times(2)).flush();
    }

    @Test
    void saveDraft_replaceChangingTheCode_isAFieldError() {
        RoutingHeader draft = draft();
        when(routingHeaderRepository.findWithOperationsByRoutingId(draft.getRoutingId())).thenReturn(Optional.of(draft));

        RoutingDraftSaveRequest renamed = new RoutingDraftSaveRequest(draft.getRoutingId(), 0L, "RT-OTHER", null,
                product.getItemId(), null, null, null, List.of(op(null, "a", 10, assembly, List.of())));
        assertThatThrownBy(() -> service.saveDraft(companyId, renamed))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("code"));
    }

    @Test
    void copy_whileTheProductHasAnOpenDraft_isRefused() {
        RoutingHeader active = draft();
        active.setStatus(RoutingStatus.ACTIVE);
        when(routingHeaderRepository.findItemIdByRoutingId(active.getRoutingId())).thenReturn(Optional.of(product.getItemId()));
        when(routingHeaderRepository.findWithOperationsByRoutingId(active.getRoutingId())).thenReturn(Optional.of(active));
        when(routingHeaderRepository.existsByCompanyCompanyIdAndItemItemIdAndStatus(
                companyId, product.getItemId(), RoutingStatus.DRAFT)).thenReturn(true);

        assertThatThrownBy(() -> service.copy(active.getRoutingId(), new RoutingCopyRequest(null, null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPEN_DRAFT_EXISTS));
    }

    @Test
    void copy_keepsTheCodeAndTheGraph_numbersTheNextVersion() {
        RoutingHeader active = draft();
        active.setStatus(RoutingStatus.ACTIVE);
        active.getOperations().get(1).getPredecessors().add(active.getOperations().get(0));
        when(routingHeaderRepository.findItemIdByRoutingId(active.getRoutingId())).thenReturn(Optional.of(product.getItemId()));
        when(routingHeaderRepository.findWithOperationsByRoutingId(active.getRoutingId())).thenReturn(Optional.of(active));
        when(routingHeaderRepository.findMaxVersionNumber(companyId, product.getItemId())).thenReturn(1);
        when(routingHeaderRepository.saveAndFlush(any(RoutingHeader.class))).thenAnswer(withIds());

        RoutingResponse copy = service.copy(active.getRoutingId(), new RoutingCopyRequest(null, null, null, null));

        assertThat(copy.code()).isEqualTo("RT-DEMO");
        assertThat(copy.versionNumber()).isEqualTo(2);
        assertThat(copy.status()).isEqualTo("DRAFT");
        assertThat(copy.operations().get(1).predecessorOperationIds())
                .containsExactly(copy.operations().get(0).routingOperationId());
        assertThat(active.getStatus()).isEqualTo(RoutingStatus.ACTIVE);
    }

    @Test
    void activate_futureEffectiveDate_isRefused() {
        RoutingHeader draft = draft();
        draft.setEffectiveFrom(Instant.now().plus(2, ChronoUnit.DAYS));
        when(routingHeaderRepository.findItemIdByRoutingId(draft.getRoutingId())).thenReturn(Optional.of(product.getItemId()));
        when(routingHeaderRepository.findWithOperationsByRoutingId(draft.getRoutingId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.activate(draft.getRoutingId(), null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.EFFECTIVE_DATE_FUTURE));
        assertThat(draft.getStatus()).isEqualTo(RoutingStatus.DRAFT);
    }

    private void stubWorkCenters() {
        lenient().when(workCenterLookupService.getActiveWorkCenter(assembly.getWorkCenterId())).thenReturn(assembly);
        lenient().when(workCenterLookupService.getActiveWorkCenter(paint.getWorkCenterId())).thenReturn(paint);
    }

    private RoutingDraftSaveRequest request(UUID routingId, Long expectedVersion, RoutingDraftOperationRequest... operations) {
        return new RoutingDraftSaveRequest(routingId, expectedVersion, "rt-demo", "Demo routing", product.getItemId(),
                null, null, null, List.of(operations));
    }

    private RoutingDraftOperationRequest op(UUID id, String key, int sequence, WorkCenter workCenter, List<String> predecessors) {
        return new RoutingDraftOperationRequest(id, key, sequence, "Op " + key, workCenter.getWorkCenterId(),
                new BigDecimal("30"), new BigDecimal("2"), predecessors);
    }

    private RoutingHeader draft() {
        RoutingHeader routing = RoutingHeader.builder()
                .routingId(UUID.randomUUID()).company(company).item(product).code("RT-DEMO")
                .routingVersion("1").versionNumber(1).status(RoutingStatus.DRAFT).operations(new ArrayList<>())
                .build();
        routing.setVersion(0L);
        routing.getOperations().add(operation(routing, 10, assembly));
        routing.getOperations().add(operation(routing, 20, paint));
        return routing;
    }

    private RoutingOperation operation(RoutingHeader routing, int sequence, WorkCenter workCenter) {
        return RoutingOperation.builder().routingOperationId(UUID.randomUUID()).routing(routing).sequence(sequence)
                .name("Op " + sequence).workCenter(workCenter)
                .setupMinutes(BigDecimal.TEN).runMinutesPerUnit(BigDecimal.ONE).build();
    }

    private WorkCenter workCenter(String code) {
        return WorkCenter.builder().workCenterId(UUID.randomUUID())
                .plant(Plant.builder().plantId(plantId).code("P1").name("P1").status(OrganizationStatus.ACTIVE).build())
                .code(code).name(code).capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
    }

    private org.mockito.stubbing.Answer<RoutingHeader> withIds() {
        return invocation -> {
            RoutingHeader routing = invocation.getArgument(0);
            if (routing.getRoutingId() == null) {
                routing.setRoutingId(UUID.randomUUID());
            }
            routing.getOperations().stream().filter(operation -> operation.getRoutingOperationId() == null)
                    .forEach(operation -> operation.setRoutingOperationId(UUID.randomUUID()));
            return routing;
        };
    }
}
