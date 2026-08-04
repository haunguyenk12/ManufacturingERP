package com.erp.manufacturing.module.routing.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
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
import com.erp.manufacturing.module.routing.dto.RoutingCreateRequest;
import com.erp.manufacturing.module.routing.dto.RoutingOperationRequest;
import com.erp.manufacturing.module.routing.dto.RoutingResponse;
import com.erp.manufacturing.module.routing.mapper.RoutingMapper;
import com.erp.manufacturing.module.routing.repository.RoutingHeaderRepository;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.service.WorkCenterLookupService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoutingService tests")
class RoutingServiceTest {

    @Mock RoutingHeaderRepository routingHeaderRepository;
    @Mock ItemLookupService itemLookupService;
    @Mock WorkCenterLookupService workCenterLookupService;

    RoutingService service;

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID WC_01_ID = UUID.randomUUID();
    private static final UUID WC_02_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RoutingService(routingHeaderRepository, itemLookupService, workCenterLookupService, new RoutingMapper());
        lenientStubWorkCenters();
    }

    /**
     * Most tests below don't care about work centers — they only need buildOperation() to resolve
     * something valid. Stubbed with lenient() so tests that never touch work centers (e.g. the
     * activate/deactivate suites, which never build operations) don't fail on unnecessary stubbing.
     */
    private void lenientStubWorkCenters() {
        lenient().when(workCenterLookupService.getActiveWorkCenter(WC_01_ID))
                .thenReturn(workCenter(WC_01_ID, PLANT_ID, "WC-01"));
        lenient().when(workCenterLookupService.getActiveWorkCenter(WC_02_ID))
                .thenReturn(workCenter(WC_02_ID, PLANT_ID, "WC-02"));
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    void create_startsAtDraftAndNormalisesCodeAndVersion() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, company, ItemType.FINISHED_GOOD));
        when(routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, "RT-FG100", "V1"))
                .thenReturn(false);
        when(routingHeaderRepository.save(any(RoutingHeader.class))).thenAnswer(saveWithGeneratedIds());

        RoutingResponse response = service.create(companyId, new RoutingCreateRequest(
                itemId, "  rt-fg100 ", " v1 ", "  First routing  ",
                List.of(
                        new RoutingOperationRequest(20, " Paint ", WC_02_ID, new BigDecimal("5"), new BigDecimal("1.5")),
                        new RoutingOperationRequest(10, " Assembly ", WC_01_ID, new BigDecimal("15"), new BigDecimal("2.5")))));

        assertThat(response.status()).isEqualTo(RoutingStatus.DRAFT.name());
        assertThat(response.code()).isEqualTo("RT-FG100");
        assertThat(response.version()).isEqualTo("V1");
        assertThat(response.note()).isEqualTo("First routing");
        // Response is ordered by sequence, not by request order.
        assertThat(response.operations()).hasSize(2);
        assertThat(response.operations().get(0).sequence()).isEqualTo(10);
        assertThat(response.operations().get(0).name()).isEqualTo("Assembly");
        assertThat(response.operations().get(0).workCenterId()).isEqualTo(WC_01_ID);
        assertThat(response.operations().get(0).workCenterCode()).isEqualTo("WC-01");
        assertThat(response.operations().get(0).setupMinutes()).isEqualByComparingTo("15");
        assertThat(response.operations().get(0).runMinutesPerUnit()).isEqualByComparingTo("2.5");
        assertThat(response.operations().get(1).sequence()).isEqualTo(20);
    }

    @Test
    void create_duplicateCodeAndVersionWithinCompany_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, company, ItemType.FINISHED_GOOD));
        when(routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, "RT-FG100", "V1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(companyId, createRequest(itemId)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(routingHeaderRepository, never()).save(any());
    }

    @Test
    void create_duplicateOperationSequence_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, company, ItemType.FINISHED_GOOD));
        when(routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, "RT-FG100", "V1"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(companyId, new RoutingCreateRequest(
                itemId, "RT-FG100", "V1", null,
                List.of(
                        new RoutingOperationRequest(10, "Assembly", WC_01_ID, BigDecimal.ZERO, BigDecimal.ONE),
                        new RoutingOperationRequest(10, "Paint", WC_02_ID, BigDecimal.ZERO, BigDecimal.ONE)))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION));

        verify(routingHeaderRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: operations referencing work centers of two different plants throw 422 (B_wc2)")
    void create_operationsAcrossTwoPlants_throwsOperationNotAllowed() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);
        UUID otherPlantId = UUID.randomUUID();
        UUID wcOtherPlantId = UUID.randomUUID();

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, company, ItemType.FINISHED_GOOD));
        when(routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, "RT-FG100", "V1"))
                .thenReturn(false);
        when(workCenterLookupService.getActiveWorkCenter(wcOtherPlantId))
                .thenReturn(workCenter(wcOtherPlantId, otherPlantId, "WC-99"));

        assertThatThrownBy(() -> service.create(companyId, new RoutingCreateRequest(
                itemId, "RT-FG100", "V1", null,
                List.of(
                        new RoutingOperationRequest(10, "Assembly", WC_01_ID, BigDecimal.ZERO, BigDecimal.ONE),
                        new RoutingOperationRequest(20, "Paint", wcOtherPlantId, BigDecimal.ZERO, BigDecimal.ONE)))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(routingHeaderRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: inactive work center is refused before any save")
    void create_inactiveWorkCenter_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Company company = company(companyId);

        when(itemLookupService.getActiveItem(itemId)).thenReturn(item(itemId, company, ItemType.FINISHED_GOOD));
        when(routingHeaderRepository.existsByCompanyCompanyIdAndCodeAndRoutingVersion(companyId, "RT-FG100", "V1"))
                .thenReturn(false);
        when(workCenterLookupService.getActiveWorkCenter(WC_01_ID))
                .thenThrow(ExceptionFactory.businessRule(
                        BusinessErrorCode.OPERATION_NOT_ALLOWED, "Inactive work center cannot be used: " + WC_01_ID));

        assertThatThrownBy(() -> service.create(companyId, createRequest(itemId)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(routingHeaderRepository, never()).save(any());
    }

    @Test
    void create_rawMaterialItem_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(itemId))
                .thenReturn(item(itemId, company(companyId), ItemType.RAW_MATERIAL));

        assertThatThrownBy(() -> service.create(companyId, createRequest(itemId)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(routingHeaderRepository, never()).save(any());
    }

    @Test
    void create_itemOfAnotherCompany_fails() {
        UUID companyId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(itemLookupService.getActiveItem(itemId))
                .thenReturn(item(itemId, company(UUID.randomUUID()), ItemType.FINISHED_GOOD));

        assertThatThrownBy(() -> service.create(companyId, createRequest(itemId)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(routingHeaderRepository, never()).save(any());
    }

    // ── activate ───────────────────────────────────────────────────────────

    @Test
    void activate_deactivatesThePreviousActiveRoutingOfTheSameItem() {
        Company company = company(UUID.randomUUID());
        Item item = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        RoutingHeader previous = routing(company, item, "RT-FG100", "V1", RoutingStatus.ACTIVE);
        RoutingHeader candidate = routing(company, item, "RT-FG100", "V2", RoutingStatus.DRAFT);

        when(routingHeaderRepository.findWithOperationsByRoutingId(candidate.getRoutingId()))
                .thenReturn(Optional.of(candidate));
        when(routingHeaderRepository.findByCompanyCompanyIdAndItemItemIdAndStatus(
                company.getCompanyId(), item.getItemId(), RoutingStatus.ACTIVE))
                .thenReturn(Optional.of(previous));
        when(routingHeaderRepository.save(any(RoutingHeader.class))).thenAnswer(returnFirstArgument());

        RoutingResponse response = service.activate(candidate.getRoutingId());

        assertThat(response.status()).isEqualTo(RoutingStatus.ACTIVE.name());
        assertThat(previous.getStatus()).isEqualTo(RoutingStatus.INACTIVE);
        verify(routingHeaderRepository).saveAndFlush(previous);
    }

    @Test
    void activate_noPreviousActiveRouting_justActivates() {
        Company company = company(UUID.randomUUID());
        Item item = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        RoutingHeader candidate = routing(company, item, "RT-FG100", "V1", RoutingStatus.DRAFT);

        when(routingHeaderRepository.findWithOperationsByRoutingId(candidate.getRoutingId()))
                .thenReturn(Optional.of(candidate));
        when(routingHeaderRepository.findByCompanyCompanyIdAndItemItemIdAndStatus(
                company.getCompanyId(), item.getItemId(), RoutingStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(routingHeaderRepository.save(any(RoutingHeader.class))).thenAnswer(returnFirstArgument());

        assertThat(service.activate(candidate.getRoutingId()).status())
                .isEqualTo(RoutingStatus.ACTIVE.name());

        verify(routingHeaderRepository, never()).saveAndFlush(any());
    }

    @Test
    void activate_alreadyActiveRouting_throwsStateConflict() {
        Company company = company(UUID.randomUUID());
        Item item = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        RoutingHeader routing = routing(company, item, "RT-FG100", "V1", RoutingStatus.ACTIVE);

        when(routingHeaderRepository.findWithOperationsByRoutingId(routing.getRoutingId()))
                .thenReturn(Optional.of(routing));

        assertThatThrownBy(() -> service.activate(routing.getRoutingId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.STATE_CONFLICT));

        verify(routingHeaderRepository, never()).save(any());
        verify(routingHeaderRepository, never()).saveAndFlush(any());
    }

    @Test
    void activate_routingWithoutOperations_fails() {
        Company company = company(UUID.randomUUID());
        Item item = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        RoutingHeader candidate = routing(company, item, "RT-FG100", "V1", RoutingStatus.DRAFT);
        candidate.getOperations().clear();

        when(routingHeaderRepository.findWithOperationsByRoutingId(candidate.getRoutingId()))
                .thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.activate(candidate.getRoutingId()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(routingHeaderRepository, never()).save(any());
    }

    @Test
    void activate_unknownRouting_throwsNotFound() {
        UUID routingId = UUID.randomUUID();
        when(routingHeaderRepository.findWithOperationsByRoutingId(routingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(routingId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    // ── deactivate ─────────────────────────────────────────────────────────

    @Test
    void deactivate_activeRouting_movesToInactive() {
        Company company = company(UUID.randomUUID());
        Item item = item(UUID.randomUUID(), company, ItemType.FINISHED_GOOD);
        RoutingHeader routing = routing(company, item, "RT-FG100", "V1", RoutingStatus.ACTIVE);

        when(routingHeaderRepository.findWithOperationsByRoutingId(routing.getRoutingId()))
                .thenReturn(Optional.of(routing));
        when(routingHeaderRepository.save(any(RoutingHeader.class))).thenAnswer(returnFirstArgument());

        assertThat(service.deactivate(routing.getRoutingId()).status())
                .isEqualTo(RoutingStatus.INACTIVE.name());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private RoutingCreateRequest createRequest(UUID itemId) {
        return new RoutingCreateRequest(itemId, "RT-FG100", "V1", null,
                List.of(new RoutingOperationRequest(10, "Assembly", WC_01_ID,
                        new BigDecimal("15"), new BigDecimal("2.5"))));
    }

    private RoutingHeader routing(Company company, Item item, String code, String version, RoutingStatus status) {
        RoutingHeader routing = RoutingHeader.builder()
                .routingId(UUID.randomUUID())
                .company(company)
                .item(item)
                .code(code)
                .routingVersion(version)
                .status(status)
                .operations(new ArrayList<>())
                .build();
        routing.getOperations().add(RoutingOperation.builder()
                .routingOperationId(UUID.randomUUID())
                .routing(routing)
                .sequence(10)
                .name("Assembly")
                .workCenter(workCenter(WC_01_ID, PLANT_ID, "WC-01"))
                .setupMinutes(new BigDecimal("15"))
                .runMinutesPerUnit(new BigDecimal("2.5"))
                .build());
        return routing;
    }

    private Company company(UUID companyId) {
        return Company.builder().companyId(companyId).code("ACME").name("ACME")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Item item(UUID itemId, Company company, ItemType type) {
        return Item.builder().itemId(itemId).company(company).code("FG-100").name("Widget")
                .type(type).unit("EA").status(ItemStatus.ACTIVE).build();
    }

    private WorkCenter workCenter(UUID workCenterId, UUID plantId, String code) {
        return WorkCenter.builder()
                .workCenterId(workCenterId)
                .plant(Plant.builder().plantId(plantId).code("PLANT").name("Plant")
                        .status(OrganizationStatus.ACTIVE).build())
                .code(code)
                .name(code)
                .capacityUnitType(com.erp.manufacturing.module.workcenter.domain.CapacityUnitType.MACHINE)
                .capacityUnits(1)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    private org.mockito.stubbing.Answer<RoutingHeader> saveWithGeneratedIds() {
        return invocation -> {
            RoutingHeader routing = invocation.getArgument(0);
            routing.setRoutingId(UUID.randomUUID());
            routing.getOperations().forEach(operation -> operation.setRoutingOperationId(UUID.randomUUID()));
            return routing;
        };
    }

    private org.mockito.stubbing.Answer<RoutingHeader> returnFirstArgument() {
        return invocation -> invocation.getArgument(0);
    }
}
