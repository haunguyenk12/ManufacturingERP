package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.MultiErrorException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomStatus;
import com.erp.manufacturing.module.bom.service.BomLookupService;
import com.erp.manufacturing.module.inventory.domain.Item;
import com.erp.manufacturing.module.organization.domain.Company;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.routing.domain.RoutingHeader;
import com.erp.manufacturing.module.routing.domain.RoutingOperation;
import com.erp.manufacturing.module.routing.domain.RoutingStatus;
import com.erp.manufacturing.module.routing.service.RoutingLookupService;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.TimeInterval;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workorder.domain.WorkOrder;
import com.erp.manufacturing.module.workorder.domain.WorkOrderOperation;
import com.erp.manufacturing.module.workorder.domain.WorkOrderStatus;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityLoadResponse;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPreviewRequest;
import com.erp.manufacturing.module.workorder.dto.capacity.CapacityPreviewResponse;
import com.erp.manufacturing.module.workorder.repository.WorkOrderOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * G5 capacity preview. The main case is FE A27 verbatim — required 1030, start 13 Oct 08:00, end
 * 14 Oct 08:35, in 960 / after 70, demand 1130 / allocated 1060 / available 960 / overload 170 — so a
 * failure is a disagreement with the example both sides signed, not with numbers made up here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CapacityPreviewService")
class CapacityPreviewServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TUE = LocalDate.of(2026, 10, 13);
    private static final LocalDate WED = TUE.plusDays(1);

    @Mock OrganizationLookupService organizationLookupService;
    @Mock RoutingLookupService routingLookupService;
    @Mock BomLookupService bomLookupService;
    @Mock WorkOrderOperationRepository workOrderOperationRepository;
    @Mock WorkCalendarLookupService workCalendarLookupService;

    CapacityPreviewService service;
    Company company;
    Plant plant;
    Item product;
    WorkCenter workCenter;
    RoutingHeader routing;

    @BeforeEach
    void setUp() {
        service = new CapacityPreviewService(organizationLookupService, routingLookupService, bomLookupService,
                new CapacityLoadCalculator(workOrderOperationRepository, workCalendarLookupService));
        company = Company.builder().companyId(UUID.randomUUID()).code("C").name("C").status(OrganizationStatus.ACTIVE).build();
        plant = Plant.builder().plantId(UUID.randomUUID()).company(company).code("P").name("P")
                .timezone(ZONE.getId()).status(OrganizationStatus.ACTIVE).build();
        product = Item.builder().itemId(UUID.randomUUID()).company(company).code("FG-DEMO").name("FG").unit("PCS").build();
        WorkCalendar calendar = WorkCalendar.builder().workCalendarId(UUID.randomUUID()).plant(plant).code("CAL").name("Cal")
                .effectiveFrom(LocalDate.of(2026, 1, 1)).status(OrganizationStatus.ACTIVE).build();
        workCenter = WorkCenter.builder().workCenterId(UUID.randomUUID()).plant(plant).code("WC-ASM").name("Assembly")
                .capacityUnitType(CapacityUnitType.LINE).capacityUnits(2).status(OrganizationStatus.ACTIVE)
                .workCalendar(calendar).build();
        routing = RoutingHeader.builder().routingId(UUID.randomUUID()).company(company).item(product).code("ROUTE-DEMO")
                .routingVersion("1").versionNumber(1).status(RoutingStatus.ACTIVE).operations(new ArrayList<>()).build();
        routing.getOperations().add(RoutingOperation.builder().routingOperationId(UUID.randomUUID()).routing(routing)
                .sequence(1).name("Lắp ráp").workCenter(workCenter)
                .setupMinutes(new BigDecimal("30")).runMinutesPerUnit(new BigDecimal("2")).build());
        lenient().when(organizationLookupService.getActivePlant(plant.getPlantId())).thenReturn(plant);
        lenient().when(routingLookupService.findRoutingWithOperations(routing.getRoutingId())).thenReturn(Optional.of(routing));
        lenient().when(bomLookupService.getActiveBom(company.getCompanyId(), product.getItemId())).thenReturn(
                BomHeader.builder().bomId(UUID.randomUUID()).company(company).parentItem(product).revision("1")
                        .bomCode("BOM-DEMO-01").versionNumber(1).status(BomStatus.ACTIVE).build());
        // FE §3.6: Tuesday and Wednesday 08:00–17:00 with lunch 12:00–13:00.
        lenient().when(workCalendarLookupService.computeWorkingIntervals(
                        eq(calendar.getWorkCalendarId()), any(), any(), eq(ZONE)))
                .thenReturn(List.of(window(TUE, 8, 12), window(TUE, 13, 17), window(WED, 8, 12), window(WED, 13, 17)));
    }

    @Test
    void preview_matchesTheExampleOfTheHandoff_A27() {
        when(workOrderOperationRepository.findScheduledOverlapping(eq(plant.getPlantId()), any(), any(), any()))
                .thenReturn(List.of(existingLoad("100", at(TUE, 8, 0), at(TUE, 9, 40))));

        CapacityPreviewResponse preview = service.preview(plant.getPlantId(),
                new CapacityPreviewRequest(routing.getRoutingId(), new BigDecimal("500"),
                        Instant.parse("2026-10-13T01:00:00Z"), TUE, TUE));

        CapacityPreviewResponse.Operation operation = preview.operations().get(0);
        assertThat(operation.requiredMinutes()).isEqualByComparingTo("1030");
        assertThat(operation.inPeriodMinutes()).isEqualByComparingTo("960");
        assertThat(operation.afterPeriodMinutes()).isEqualByComparingTo("70");
        assertThat(operation.proposedStart()).isEqualTo(Instant.parse("2026-10-13T01:00:00Z"));
        assertThat(operation.proposedEnd()).isEqualTo(Instant.parse("2026-10-14T01:35:00Z"));
        assertThat(operation.segments()).extracting(CapacityPreviewResponse.Segment::capacityMinutes)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("480"), new BigDecimal("480"), new BigDecimal("70"));

        CapacityLoadResponse load = preview.loads().get(0);
        assertThat(load.baseInPeriodMinutes()).isEqualByComparingTo("100");
        assertThat(load.previewRequiredMinutes()).isEqualByComparingTo("1030");
        assertThat(load.demandMinutes()).isEqualByComparingTo("1130");
        assertThat(load.allocatedMinutes()).isEqualByComparingTo("1060");
        assertThat(load.availableMinutes()).isEqualByComparingTo("960");
        assertThat(load.overloadMinutes()).isEqualByComparingTo("170");
        assertThat(load.status()).isEqualTo("OVERLOADED");
        assertThat(preview.timezone()).isEqualTo(ZONE.getId());
        assertThat(preview.sourceVersions().routingVersionNumber()).isEqualTo(1);
        assertThat(preview.sourceVersions().bomVersionNumber()).isEqualTo(1);

        // FE A30: a preview only reads.
        verify(workOrderOperationRepository).findScheduledOverlapping(eq(plant.getPlantId()), any(), any(), any());
        verifyNoMoreInteractions(workOrderOperationRepository);
    }

    @Test
    void preview_inactiveRouting_isAStateConflict() {
        routing.setStatus(RoutingStatus.INACTIVE);

        assertThatThrownBy(() -> service.preview(plant.getPlantId(), request("500", TUE, TUE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(BusinessErrorCode.STATE_CONFLICT));
        verifyNoInteractions(workOrderOperationRepository);
    }

    /** FE A29: no calendar means "cannot schedule", never "schedule in continuous time". */
    @Test
    void preview_workCenterWithoutCalendar_isSchedulingUnavailable() {
        workCenter.setWorkCalendar(null);

        assertThatThrownBy(() -> service.preview(plant.getPlantId(), request("500", TUE, TUE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.SCHEDULING_UNAVAILABLE));
    }

    @Test
    void preview_noWorkingTimeWithinTheHorizon_isSchedulingUnavailable() {
        when(workCalendarLookupService.computeWorkingIntervals(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.preview(plant.getPlantId(), request("500", TUE, TUE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.SCHEDULING_UNAVAILABLE));
    }

    @Test
    void preview_startOutsideThePeriod_isAFieldError() {
        assertThatThrownBy(() -> service.preview(plant.getPlantId(), new CapacityPreviewRequest(routing.getRoutingId(),
                BigDecimal.TEN, Instant.parse("2026-10-14T01:00:00Z"), TUE, TUE)))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("requestedStart"));
    }

    @Test
    void preview_periodLongerThanNinetyDays_isAFieldError() {
        assertThatThrownBy(() -> service.preview(plant.getPlantId(), request("1", TUE, TUE.plusDays(90))))
                .isInstanceOf(MultiErrorException.class)
                .satisfies(ex -> assertThat(((MultiErrorException) ex).getFieldErrors()).containsKey("periodEnd"));
        verifyNoInteractions(organizationLookupService, routingLookupService);
    }

    /** Another company's routing is answered like a missing one. */
    @Test
    void preview_routingOfAnotherCompany_isNotFound() {
        routing.setCompany(Company.builder().companyId(UUID.randomUUID()).build());

        assertThatThrownBy(() -> service.preview(plant.getPlantId(), request("500", TUE, TUE)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    private CapacityPreviewRequest request(String quantity, LocalDate from, LocalDate to) {
        return new CapacityPreviewRequest(routing.getRoutingId(), new BigDecimal(quantity),
                Instant.parse("2026-10-13T01:00:00Z"), from, to);
    }

    private WorkOrderOperation existingLoad(String minutes, Instant start, Instant end) {
        WorkOrder workOrder = WorkOrder.builder().workOrderId(UUID.randomUUID()).plant(plant)
                .plannedQuantity(BigDecimal.ONE).status(WorkOrderStatus.RELEASED).build();
        return WorkOrderOperation.builder().workOrderOperationId(UUID.randomUUID()).workOrder(workOrder).sequence(1)
                .name("Existing").workCenterCode(workCenter.getCode()).workCenter(workCenter)
                .setupMinutes(new BigDecimal(minutes)).runMinutesPerUnit(BigDecimal.ZERO)
                .plannedStartAt(start).plannedEndAt(end).build();
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    private static TimeInterval window(LocalDate day, int from, int to) {
        return new TimeInterval(at(day, from, 0), at(day, to, 0));
    }
}
