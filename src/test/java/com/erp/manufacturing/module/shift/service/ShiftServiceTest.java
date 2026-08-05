package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.organization.domain.Plant;
import com.erp.manufacturing.module.organization.service.OrganizationLookupService;
import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.dto.ShiftBreakRequest;
import com.erp.manufacturing.module.shift.dto.ShiftCreateRequest;
import com.erp.manufacturing.module.shift.dto.ShiftUpdateRequest;
import com.erp.manufacturing.module.shift.mapper.ShiftMapper;
import com.erp.manufacturing.module.shift.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

@DisplayName("ShiftService")
class ShiftServiceTest {

    private final ShiftRepository repository = mock(ShiftRepository.class);
    private final OrganizationLookupService organizationLookupService = mock(OrganizationLookupService.class);
    private final ShiftService service = new ShiftService(repository, organizationLookupService, new ShiftMapper());

    private static final UUID PLANT_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID = UUID.randomUUID();

    @BeforeEach
    void stubSave() {
        when(repository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Plant activePlant(UUID plantId) {
        return Plant.builder().plantId(plantId).code("PLANT").name("Plant")
                .status(OrganizationStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("create: normalizes the code to upper case and persists it under the given plant")
    void create_normalizesCodeToUpperCase() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-01")).thenReturn(false);

        var response = service.create(PLANT_ID, new ShiftCreateRequest(
                "sh-01", "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0), null));

        assertThat(response.code()).isEqualTo("SH-01");
        assertThat(response.plantId()).isEqualTo(PLANT_ID);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.breaks()).isEmpty();
    }

    @Test
    @DisplayName("create: duplicate code within the same plant throws RESOURCE_ALREADY_EXISTS before any save")
    void create_duplicateCodeInSamePlant_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(PLANT_ID, new ShiftCreateRequest(
                "sh-01", "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0), null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ValidationErrorCode.RESOURCE_ALREADY_EXISTS));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: a break inside a same-day shift is accepted, including one touching the boundary")
    void create_breakWithinSameDayShift_isAccepted() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-01")).thenReturn(false);

        var response = service.create(PLANT_ID, new ShiftCreateRequest(
                "SH-01", "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new ShiftBreakRequest(LocalTime.of(12, 0), LocalTime.of(13, 0)),
                        new ShiftBreakRequest(LocalTime.of(16, 0), LocalTime.of(17, 0)))));

        assertThat(response.breaks()).hasSize(2);
    }

    @Test
    @DisplayName("create: a break outside the shift window throws OPERATION_NOT_ALLOWED before any save")
    void create_breakOutsideShiftWindow_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-01")).thenReturn(false);

        assertThatThrownBy(() -> service.create(PLANT_ID, new ShiftCreateRequest(
                "SH-01", "Day Shift", LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(new ShiftBreakRequest(LocalTime.of(17, 0), LocalTime.of(18, 0))))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create: a break crossing the overnight shift's midnight boundary is accepted")
    void create_breakCrossingMidnightOfOvernightShift_isAccepted() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-NIGHT")).thenReturn(false);

        // Overnight shift 22:00-06:00; break 23:30-00:30 crosses midnight but still lies inside it.
        var response = service.create(PLANT_ID, new ShiftCreateRequest(
                "SH-NIGHT", "Night Shift", LocalTime.of(22, 0), LocalTime.of(6, 0),
                List.of(new ShiftBreakRequest(LocalTime.of(23, 30), LocalTime.of(0, 30)))));

        assertThat(response.breaks()).hasSize(1);
    }

    @Test
    @DisplayName("create: a break outside an overnight shift's window (daytime) throws OPERATION_NOT_ALLOWED")
    void create_breakOutsideOvernightShiftWindow_throws() {
        when(organizationLookupService.getActivePlant(PLANT_ID)).thenReturn(activePlant(PLANT_ID));
        when(repository.existsByPlantPlantIdAndCode(PLANT_ID, "SH-NIGHT")).thenReturn(false);

        assertThatThrownBy(() -> service.create(PLANT_ID, new ShiftCreateRequest(
                "SH-NIGHT", "Night Shift", LocalTime.of(22, 0), LocalTime.of(6, 0),
                List.of(new ShiftBreakRequest(LocalTime.of(10, 0), LocalTime.of(11, 0))))))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update: null breaks leaves the existing breaks unchanged")
    void update_nullBreaks_leavesExistingBreaksUnchanged() {
        Shift existing = Shift.builder()
                .shiftId(SHIFT_ID).plant(activePlant(PLANT_ID)).code("SH-01").name("Day Shift")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .status(OrganizationStatus.ACTIVE).build();
        existing.getBreaks().add(ShiftBreak.builder()
                .shift(existing).startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0)).build());
        when(repository.findById(SHIFT_ID)).thenReturn(Optional.of(existing));

        var response = service.update(SHIFT_ID, new ShiftUpdateRequest("New name", null, null, null));

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.breaks()).hasSize(1);
    }

    @Test
    @DisplayName("update: empty breaks list clears all existing breaks")
    void update_emptyBreaksList_clearsAllBreaks() {
        Shift existing = Shift.builder()
                .shiftId(SHIFT_ID).plant(activePlant(PLANT_ID)).code("SH-01").name("Day Shift")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .status(OrganizationStatus.ACTIVE).build();
        existing.getBreaks().add(ShiftBreak.builder()
                .shift(existing).startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(13, 0)).build());
        when(repository.findById(SHIFT_ID)).thenReturn(Optional.of(existing));

        var response = service.update(SHIFT_ID, new ShiftUpdateRequest(null, null, null, List.of()));

        assertThat(response.breaks()).isEmpty();
    }

    @Test
    @DisplayName("update: shrinking the window without resending breaks re-validates the existing breaks")
    void update_shrinkingWindowWithoutResendingBreaks_reValidatesExistingBreaks() {
        Shift existing = Shift.builder()
                .shiftId(SHIFT_ID).plant(activePlant(PLANT_ID)).code("SH-01").name("Day Shift")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .status(OrganizationStatus.ACTIVE).build();
        existing.getBreaks().add(ShiftBreak.builder()
                .shift(existing).startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(17, 0)).build());
        when(repository.findById(SHIFT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(SHIFT_ID,
                new ShiftUpdateRequest(null, null, LocalTime.of(15, 0), null)))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(BusinessErrorCode.OPERATION_NOT_ALLOWED));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate then activate: round-trips status without checking any reference")
    void deactivateThenActivate_roundTrips() {
        Shift existing = Shift.builder()
                .shiftId(SHIFT_ID).plant(activePlant(PLANT_ID)).code("SH-01").name("Day Shift")
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .status(OrganizationStatus.ACTIVE).build();
        when(repository.findById(SHIFT_ID)).thenReturn(Optional.of(existing));

        assertThat(service.deactivate(SHIFT_ID).status()).isEqualTo("INACTIVE");
        assertThat(service.activate(SHIFT_ID).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("get: unknown id throws RESOURCE_NOT_FOUND")
    void get_unknownId_throwsResourceNotFound() {
        when(repository.findById(SHIFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SHIFT_ID))
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
