package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.Weekday;
import com.erp.manufacturing.module.shift.dto.WorkCalendarCreateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarExceptionRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarUpdateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarWeeklyShiftRequest;
import com.erp.manufacturing.module.shift.mapper.WorkCalendarMapper;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WorkCalendarService")
class WorkCalendarServiceTest {

    private final WorkCalendarRepository repository = mock(WorkCalendarRepository.class);
    private final ShiftRepository shiftRepository = mock(ShiftRepository.class);
    private final OrganizationLookupService organizationLookupService = mock(OrganizationLookupService.class);
    private final WorkCalendarService service = new WorkCalendarService(
            repository, shiftRepository, organizationLookupService, new WorkCalendarMapper());

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID CALENDAR_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID = UUID.randomUUID();

    @BeforeEach
    void stubSave() {
        when(repository.save(any(WorkCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Plant activePlant(UUID plantId) {
        return Plant.builder().plantId(plantId).code("PLANT").name("Plant")
                .status(OrganizationStatus.ACTIVE).build();
    }

    private Shift shiftInPlant(UUID shiftId, UUID plantId) {
        return Shift.builder().shiftId(shiftId).plant(activePlant(plantId)).code("SH-01").name("Day")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .status(OrganizationStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("create: normalizes the code to upper case and persists it under the given plant")
    void create_normalizesCodeToUpperCase() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);

        var response = service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "cal-01", "Default Calendar", LocalDate.of(2026, 1, 1), null, null, null));

        assertThat(response.code()).isEqualTo("CAL-01");
        assertThat(response.plantId()).isEqualTo(PLANT_ID);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.weeklyShifts()).isEmpty();
        assertThat(response.exceptions()).isEmpty();
    }

    @Test
    @DisplayName("create: duplicate code within the same plant throws RESOURCE_ALREADY_EXISTS before any save")
    void create_duplicateCodeInSamePlant_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "cal-01", "Default Calendar", LocalDate.of(2026, 1, 1), null, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: effectiveTo before effectiveFrom throws OPERATION_NOT_ALLOWED before any save")
    void create_effectiveToBeforeEffectiveFrom_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: a weekday may carry more than one shift")
    void create_weekdayWithMultipleShifts_isAllowed() {
        UUID nightShiftId = UUID.randomUUID();
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);
        when(shiftRepository.findAllById(any())).thenReturn(
                List.of(shiftInPlant(SHIFT_ID, PLANT_ID), shiftInPlant(nightShiftId, PLANT_ID)));

        var response = service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 1, 1), null,
                List.of(new WorkCalendarWeeklyShiftRequest(Weekday.MONDAY, SHIFT_ID),
                        new WorkCalendarWeeklyShiftRequest(Weekday.MONDAY, nightShiftId)),
                null));

        assertThat(response.weeklyShifts()).hasSize(2);
    }

    @Test
    @DisplayName("create: a shift belonging to a different plant throws RESOURCE_SCOPE_MISMATCH before any save")
    void create_shiftOfDifferentPlant_throws() {
        UUID otherPlantId = UUID.randomUUID();
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);
        when(shiftRepository.findAllById(any())).thenReturn(List.of(shiftInPlant(SHIFT_ID, otherPlantId)));

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 1, 1), null,
                List.of(new WorkCalendarWeeklyShiftRequest(Weekday.MONDAY, SHIFT_ID)), null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.RESOURCE_SCOPE_MISMATCH));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: an unknown shiftId throws RESOURCE_NOT_FOUND before any save")
    void create_unknownShiftId_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);
        when(shiftRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 1, 1), null,
                List.of(new WorkCalendarWeeklyShiftRequest(Weekday.MONDAY, SHIFT_ID)), null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: duplicate exception dates within the same request throw before any save")
    void create_duplicateExceptionDates_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "CAL-01")).thenReturn(false);

        assertThatThrownBy(() -> service.create(PLANT_ID, new WorkCalendarCreateRequest(
                "CAL-01", "Default Calendar", LocalDate.of(2026, 1, 1), null, null,
                List.of(new WorkCalendarExceptionRequest(LocalDate.of(2026, 2, 1), "Tet"),
                        new WorkCalendarExceptionRequest(LocalDate.of(2026, 2, 1), "Tet again")))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.BUSINESS_RULE_VIOLATION));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update: null weeklyShifts/exceptions leave the existing lists unchanged")
    void update_nullLists_leaveExistingListsUnchanged() {
        WorkCalendar existing = existingCalendar();
        when(repository.findWithWeeklyShiftsByWorkCalendarId(CALENDAR_ID)).thenReturn(Optional.of(existing));

        var response = service.update(CALENDAR_ID, new WorkCalendarUpdateRequest("New name", null, null, null, null));

        assertThat(response.name()).isEqualTo("New name");
    }

    @Test
    @DisplayName("update: empty exceptions list clears all existing exceptions")
    void update_emptyExceptionsList_clearsAllExceptions() {
        WorkCalendar existing = existingCalendar();
        existing.getExceptions().add(com.erp.manufacturing.module.shift.domain.WorkCalendarException.builder()
                .workCalendar(existing).exceptionDate(LocalDate.of(2026, 2, 1)).reason("Tet").build());
        when(repository.findWithWeeklyShiftsByWorkCalendarId(CALENDAR_ID)).thenReturn(Optional.of(existing));

        var response = service.update(CALENDAR_ID, new WorkCalendarUpdateRequest(null, null, null, null, List.of()));

        assertThat(response.exceptions()).isEmpty();
    }

    private WorkCalendar existingCalendar() {
        return WorkCalendar.builder()
                .workCalendarId(CALENDAR_ID).plant(activePlant(PLANT_ID)).code("CAL-01").name("Default Calendar")
                .effectiveFrom(LocalDate.of(2026, 1, 1)).status(OrganizationStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("deactivate then activate: round-trips status without checking any reference")
    void deactivateThenActivate_roundTrips() {
        WorkCalendar existing = existingCalendar();
        when(repository.findById(CALENDAR_ID)).thenReturn(Optional.of(existing));

        assertThat(service.deactivate(CALENDAR_ID).status()).isEqualTo("INACTIVE");
        assertThat(service.activate(CALENDAR_ID).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("get: unknown id throws RESOURCE_NOT_FOUND")
    void get_unknownId_throwsResourceNotFound() {
        when(repository.findWithWeeklyShiftsByWorkCalendarId(CALENDAR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CALENDAR_ID))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("list: passes the plantId and status straight through to the repository")
    void list_passesFiltersThrough() {
        when(repository.search(org.mockito.ArgumentMatchers.eq(PLANT_ID),
                org.mockito.ArgumentMatchers.eq(OrganizationStatus.ACTIVE), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(PLANT_ID, OrganizationStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 20));

        verify(repository).search(org.mockito.ArgumentMatchers.eq(PLANT_ID),
                org.mockito.ArgumentMatchers.eq(OrganizationStatus.ACTIVE), any());
    }
}
