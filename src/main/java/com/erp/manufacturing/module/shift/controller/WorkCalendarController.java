package com.erp.manufacturing.module.shift.controller;

import com.erp.manufacturing.common.response.ApiResponse;
import com.erp.manufacturing.common.response.PageResult;
import com.erp.manufacturing.common.web.PageableFactory;
import com.erp.manufacturing.module.organization.domain.OrganizationStatus;
import com.erp.manufacturing.module.shift.dto.WorkCalendarCreateRequest;
import com.erp.manufacturing.module.shift.dto.WorkCalendarResponse;
import com.erp.manufacturing.module.shift.dto.WorkCalendarUpdateRequest;
import com.erp.manufacturing.module.shift.service.WorkCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Work Calendar", description = "Weekly shift pattern + non-working exceptions for a plant")
public class WorkCalendarController {

    private final WorkCalendarService workCalendarService;

    @PostMapping("/api/v1/plants/{plantId}/work-calendars")
    @Operation(summary = "Create a work calendar under a plant")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> create(
            @PathVariable UUID plantId, @Valid @RequestBody WorkCalendarCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(workCalendarService.create(plantId, request)));
    }

    @GetMapping("/api/v1/plants/{plantId}/work-calendars")
    @Operation(summary = "List work calendars of a plant")
    public ResponseEntity<ApiResponse<PageResult<WorkCalendarResponse>>> list(
            @PathVariable UUID plantId,
            @RequestParam(required = false) OrganizationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.ok(workCalendarService.list(
                plantId, status, PageableFactory.of(page, size, sortBy, sortDir))));
    }

    @GetMapping("/api/v1/work-calendars/{calendarId}")
    @Operation(summary = "Get a work calendar")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> get(@PathVariable UUID calendarId) {
        return ResponseEntity.ok(ApiResponse.ok(workCalendarService.get(calendarId)));
    }

    @PatchMapping("/api/v1/work-calendars/{calendarId}")
    @Operation(summary = "Update name/effective range/weekly shifts/exceptions of a work calendar",
            description = "code and plantId are immutable after creation and are not part of this request body.")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> update(
            @PathVariable UUID calendarId, @Valid @RequestBody WorkCalendarUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(workCalendarService.update(calendarId, request)));
    }

    @PostMapping("/api/v1/work-calendars/{calendarId}/activate")
    @Operation(summary = "Activate a work calendar")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> activate(@PathVariable UUID calendarId) {
        return ResponseEntity.ok(ApiResponse.ok(workCalendarService.activate(calendarId)));
    }

    @PostMapping("/api/v1/work-calendars/{calendarId}/deactivate")
    @Operation(summary = "Deactivate a work calendar")
    public ResponseEntity<ApiResponse<WorkCalendarResponse>> deactivate(@PathVariable UUID calendarId) {
        return ResponseEntity.ok(ApiResponse.ok(workCalendarService.deactivate(calendarId)));
    }

    @DeleteMapping("/api/v1/work-calendars/{calendarId}")
    @Operation(summary = "Deactivate a work calendar (soft) — this IS the deactivate command",
            description = "Rule C6 forbids hard-deleting master data, so DELETE calls the same "
                    + "service method as POST .../deactivate; nothing is removed.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID calendarId) {
        workCalendarService.deactivate(calendarId);
        return ResponseEntity.ok(ApiResponse.noContent("Work calendar deactivated successfully"));
    }
}
