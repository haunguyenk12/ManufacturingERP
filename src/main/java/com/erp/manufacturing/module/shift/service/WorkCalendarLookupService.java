package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for other modules that need Work Calendar master data (rule C7) — {@code workcenter}
 * uses it to resolve {@code WorkCenterCreateRequest/UpdateRequest.workCalendarId} without touching
 * {@link WorkCalendarRepository} directly. No {@code @PreAuthorize}: callers are already authorized
 * on their own aggregate — same pattern as {@code WorkCenterLookupService}.
 */
@Service
@RequiredArgsConstructor
public class WorkCalendarLookupService {

    private final WorkCalendarRepository workCalendarRepository;

    @Transactional(readOnly = true)
    public WorkCalendar getActiveWorkCalendar(UUID workCalendarId) {
        WorkCalendar calendar = workCalendarRepository.findById(workCalendarId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work calendar", workCalendarId));
        if (!calendar.isActive()) {
            throw ExceptionFactory.businessRule(BusinessErrorCode.OPERATION_NOT_ALLOWED,
                    "Inactive work calendar cannot be used: " + workCalendarId);
        }
        return calendar;
    }

    /**
     * Net working windows per calendar date over {@code [from, to]} (C2-7 Part D). Internal only —
     * no controller calls this yet; {@code C2-8} (Capacity Board) is the first real caller (decision
     * §1.4, NEXT_PHASE_PLAN.md).
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<WorkingInterval>> computeWorkingWindows(UUID workCalendarId, LocalDate from, LocalDate to) {
        WorkCalendar calendar = workCalendarRepository.findWithWeeklyShiftsByWorkCalendarId(workCalendarId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work calendar", workCalendarId));
        // Triggers the lazy `exceptions` collection while the session is still open — deliberately
        // not part of the @EntityGraph above (see WorkCalendarRepository javadoc: two bag
        // collections cannot be join-fetched together).
        calendar.getExceptions().size();

        Map<LocalDate, List<WorkingInterval>> windows = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            windows.put(date, new ArrayList<>(WorkingWindowCalculator.computeDay(calendar, date)));
        }
        return windows;
    }
}
