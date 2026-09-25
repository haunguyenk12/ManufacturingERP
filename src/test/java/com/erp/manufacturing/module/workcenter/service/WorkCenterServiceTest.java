package com.erp.manufacturing.module.workcenter.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.service.WorkCalendarLookupService;
import com.erp.manufacturing.module.workcenter.domain.CapacityUnitType;
import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterCreateRequest;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterUpdateRequest;
import com.erp.manufacturing.module.workcenter.mapper.WorkCenterMapper;
import com.erp.manufacturing.module.workcenter.repository.WorkCenterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WorkCenterService")
class WorkCenterServiceTest {

    private final WorkCenterRepository repository = mock(WorkCenterRepository.class);
    private final OrganizationLookupService organizationLookupService = mock(OrganizationLookupService.class);
    private final WorkCalendarLookupService workCalendarLookupService = mock(WorkCalendarLookupService.class);
    private final WorkCenterService service =
            new WorkCenterService(repository, organizationLookupService, workCalendarLookupService, new WorkCenterMapper());

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID WORK_CENTER_ID = UUID.randomUUID();
    private static final UUID WORK_CALENDAR_ID = UUID.randomUUID();

    @BeforeEach
    void stubSave() {
        when(repository.save(any(WorkCenter.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Plant activePlant(UUID plantId) {
        return Plant.builder().plantId(plantId).code("PLANT").name("Plant")
                .status(OrganizationStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("create: normalizes the code to upper case and persists it under the given plant")
    void create_normalizesCodeToUpperCase() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "WC-01")).thenReturn(false);

        var response = service.create(PLANT_ID, new WorkCenterCreateRequest(
                "wc-01", "Assembly Line 1", null, CapacityUnitType.LINE, 2, null));

        assertThat(response.code()).isEqualTo("WC-01");
        assertThat(response.plantId()).isEqualTo(PLANT_ID);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.capacityUnitType()).isEqualTo("LINE");
        assertThat(response.capacityUnits()).isEqualTo(2);
    }

    @Test
    @DisplayName("create: duplicate code within the same plant throws RESOURCE_ALREADY_EXISTS before any save")
    void create_duplicateCodeInSamePlant_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "WC-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCenterCreateRequest(
                "wc-01", "Assembly Line 1", null, CapacityUnitType.LINE, 2, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: same code is allowed under a different plant — uniqueness is (plant, code)")
    void create_sameCodeDifferentPlant_isAllowed() {
        UUID otherPlantId = UUID.randomUUID();
        when(organizationLookupService.getActivePlant(otherPlantId)).thenReturn(activePlant(otherPlantId));
        when(repository.existsByPlantPlantIdAndCode(otherPlantId, "WC-01")).thenReturn(false);

        var response = service.create(otherPlantId, new WorkCenterCreateRequest(
                "wc-01", "Assembly Line 1", null, CapacityUnitType.LINE, 2, null));

        assertThat(response.code()).isEqualTo("WC-01");
        assertThat(response.plantId()).isEqualTo(otherPlantId);
    }

    @Test
    @DisplayName("create: propagates the inactive-plant rejection from OrganizationLookupService")
    void create_inactivePlant_propagatesRejection() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenThrow(
                ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Inactive plant cannot be used: " + PLANT_ID));

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCenterCreateRequest(
                "WC-01", "Assembly Line 1", null, CapacityUnitType.LINE, 2, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update: code and plantId are not settable — WorkCenterUpdateRequest has neither component")
    void update_hasNoCodeOrPlantIdField() {
        assertThat(WorkCenterUpdateRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("name", "description", "capacityUnitType", "capacityUnits", "workCalendarId")
                .doesNotContain("code", "plantId");
    }

    @Test
    @DisplayName("update: only touches fields that are non-null in the request")
    void update_leavesUnspecifiedFieldsUnchanged() {
        WorkCenter existing = WorkCenter.builder()
                .workCenterId(WORK_CENTER_ID).plant(activePlant(PLANT_ID)).code("WC-01").name("Old Name")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
        when(repository.findById(WORK_CENTER_ID)).thenReturn(Optional.of(existing));

        var response = service.update(WORK_CENTER_ID,
                new WorkCenterUpdateRequest(null, "new description", null, 5, null));

        assertThat(response.name()).isEqualTo("Old Name");
        assertThat(response.description()).isEqualTo("new description");
        assertThat(response.capacityUnitType()).isEqualTo("MACHINE");
        assertThat(response.capacityUnits()).isEqualTo(5);
        assertThat(response.workCalendarId()).isNull();
    }

    private WorkCalendar activeWorkCalendar(UUID workCalendarId, UUID plantId) {
        return WorkCalendar.builder().workCalendarId(workCalendarId).plant(activePlant(plantId))
                .code("CAL-1").name("Calendar").effectiveFrom(java.time.LocalDate.of(2026, 1, 1))
                .status(OrganizationStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("create: work calendar in the same plant is attached (B_wc4)")
    void create_workCalendarInSamePlant_isAttached() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "WC-01")).thenReturn(false);
        when(workCalendarLookupService.getActiveWorkCalendar(WORK_CALENDAR_ID))
                .thenReturn(activeWorkCalendar(WORK_CALENDAR_ID, PLANT_ID));

        var response = service.create(PLANT_ID, new WorkCenterCreateRequest(
                "WC-01", "Line 1", null, CapacityUnitType.LINE, 1, WORK_CALENDAR_ID));

        assertThat(response.workCalendarId()).isEqualTo(WORK_CALENDAR_ID);
    }

    @Test
    @DisplayName("create: work calendar of a different plant throws RESOURCE_SCOPE_MISMATCH before any save (B_wc4)")
    void create_workCalendarOfDifferentPlant_throws() {
        UUID otherPlantId = UUID.randomUUID();
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "WC-01")).thenReturn(false);
        when(workCalendarLookupService.getActiveWorkCalendar(WORK_CALENDAR_ID))
                .thenReturn(activeWorkCalendar(WORK_CALENDAR_ID, otherPlantId));

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCenterCreateRequest(
                "WC-01", "Line 1", null, CapacityUnitType.LINE, 1, WORK_CALENDAR_ID)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: inactive work calendar propagates the rejection from WorkCalendarLookupService (B_wc4)")
    void create_inactiveWorkCalendar_propagatesRejection() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "WC-01")).thenReturn(false);
        when(workCalendarLookupService.getActiveWorkCalendar(WORK_CALENDAR_ID)).thenThrow(
                ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                        "Inactive work calendar cannot be used: " + WORK_CALENDAR_ID));

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCenterCreateRequest(
                "WC-01", "Line 1", null, CapacityUnitType.LINE, 1, WORK_CALENDAR_ID)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_INACTIVE));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update: work calendar of a different plant throws RESOURCE_SCOPE_MISMATCH (B_wc4)")
    void update_workCalendarOfDifferentPlant_throws() {
        UUID otherPlantId = UUID.randomUUID();
        WorkCenter existing = WorkCenter.builder()
                .workCenterId(WORK_CENTER_ID).plant(activePlant(PLANT_ID)).code("WC-01").name("Line 1")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
        when(repository.findById(WORK_CENTER_ID)).thenReturn(Optional.of(existing));
        when(workCalendarLookupService.getActiveWorkCalendar(WORK_CALENDAR_ID))
                .thenReturn(activeWorkCalendar(WORK_CALENDAR_ID, otherPlantId));

        assertThatThrownBy(() -> service.update(WORK_CENTER_ID,
                new WorkCenterUpdateRequest(null, null, null, null, WORK_CALENDAR_ID)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate then activate: round-trips status without checking any reference (B_wc3)")
    void deactivateThenActivate_roundTrips() {
        WorkCenter existing = WorkCenter.builder()
                .workCenterId(WORK_CENTER_ID).plant(activePlant(PLANT_ID)).code("WC-01").name("Line 1")
                .capacityUnitType(CapacityUnitType.MACHINE).capacityUnits(1)
                .status(OrganizationStatus.ACTIVE).build();
        when(repository.findById(WORK_CENTER_ID)).thenReturn(Optional.of(existing));

        assertThat(service.deactivate(WORK_CENTER_ID).status()).isEqualTo("INACTIVE");
        assertThat(service.activate(WORK_CENTER_ID).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("get: unknown id throws RESOURCE_NOT_FOUND")
    void get_unknownId_throwsResourceNotFound() {
        when(repository.findById(WORK_CENTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(WORK_CENTER_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("list: passes the plantId and status straight through to the repository")
    void list_passesFiltersThrough() {
        when(repository.search(eq(PLANT_ID), eq(OrganizationStatus.ACTIVE), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(PLANT_ID, OrganizationStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(repository).search(eq(PLANT_ID), eq(OrganizationStatus.ACTIVE), any());
    }
}
