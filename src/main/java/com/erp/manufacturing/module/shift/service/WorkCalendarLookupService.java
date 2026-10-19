package com.erp.manufacturing.module.shift.service;

import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.repository.WorkCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.TreeSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
            throw ExceptionFactory.businessRule(BusinessErrorCode.RESOURCE_INACTIVE,
                    "Inactive work calendar cannot be used: " + workCalendarId);
        }
        return calendar;
    }

    /**
     * Net working windows per calendar date over {@code [from, to]} (C2-7 Part D). {@code C2-8}
     * (Capacity Board) is the first real caller (decision §1.4, NEXT_PHASE_PLAN.md).
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<WorkingInterval>> computeWorkingWindows(UUID workCalendarId, LocalDate from, LocalDate to) {
        WorkCalendar calendar = loadWithExceptions(workCalendarId);
        Map<LocalDate, List<WorkingInterval>> windows = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            windows.put(date, new ArrayList<>(WorkingWindowCalculator.computeDay(calendar, date)));
        }
        return windows;
    }

    /**
     * Forward-schedules {@code durationMinutes} of work no earlier than {@code earliestStart},
     * skipping non-working time on this calendar (C2-8 §"module/shift — new forward-scheduling
     * primitive"). Thin wrapper: this class owns loading the calendar (rule C7 entry point for
     * {@code workorder}), {@link WorkingWindowCalculator#advance} owns the pure day-walking math.
     *
     * <p>{@code zone} is the caller's plant timezone ({@code Plant.timezone}) — the boundary between
     * this module's naive {@link LocalDateTime} calendar model (no zone anywhere in
     * {@code module/shift}) and the {@link Instant} timestamps every other module uses.
     */
    @Transactional(readOnly = true)
    public Instant computeEndInstant(UUID workCalendarId, Instant earliestStart, ZoneId zone, long durationMinutes) {
        WorkCalendar calendar = loadWithExceptions(workCalendarId);
        LocalDateTime localEarliestStart = LocalDateTime.ofInstant(earliestStart, zone);
        LocalDateTime localEnd = WorkingWindowCalculator.advance(calendar, localEarliestStart, durationMinutes);
        return localEnd.atZone(zone).toInstant();
    }

    /**
     * Net working time of the calendar inside {@code [from, to)}, as absolute instants in the plant's
     * {@code zone}, sorted and with overlaps merged (G5). Three things this adds over
     * {@link #computeWorkingWindows}:
     *
     * <ul>
     *   <li>the day before {@code from} is evaluated too, so the tail of an overnight shift that
     *       started then is counted — FE A21: shift 22:00–06:00 with a 02:00–02:30 break gives the
     *       next day 330 minutes;</li>
     *   <li>holidays and the effective range still apply by the date a shift <em>starts</em>, exactly
     *       as {@link WorkingWindowCalculator#computeDay} decides them;</li>
     *   <li>two shifts that overlap on one calendar are merged, so the same minute never counts twice.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<TimeInterval> computeWorkingIntervals(UUID workCalendarId, Instant from, Instant to, ZoneId zone) {
        WorkCalendar calendar = loadWithExceptions(workCalendarId);
        List<TimeInterval> raw = new ArrayList<>();
        LocalDate first = LocalDateTime.ofInstant(from, zone).toLocalDate().minusDays(1);
        LocalDate last = LocalDateTime.ofInstant(to, zone).toLocalDate();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            for (WorkingInterval interval : WorkingWindowCalculator.computeDay(calendar, date)) {
                TimeInterval clipped = new TimeInterval(interval.start().atZone(zone).toInstant(),
                        interval.end().atZone(zone).toInstant()).clip(from, to);
                if (clipped != null) {
                    raw.add(clipped);
                }
            }
        }
        raw.sort(Comparator.comparing(TimeInterval::start));
        List<TimeInterval> merged = new ArrayList<>();
        for (TimeInterval interval : raw) {
            TimeInterval previous = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (previous != null && !interval.start().isAfter(previous.end())) {
                if (interval.end().isAfter(previous.end())) {
                    merged.set(merged.size() - 1, new TimeInterval(previous.start(), interval.end()));
                }
            } else {
                merged.add(interval);
            }
        }
        return merged;
    }

    /**
     * {@code NON_WORKING} exception dates of this calendar within {@code [from, to]} (C2-8 Capacity
     * Board — "calendar/shift exceptions ảnh hưởng"). Separate from
     * {@link #computeWorkingWindows}/{@link #computeEndInstant} because those already fold exception
     * days into "zero working minutes that day", which is indistinguishable from "no shift assigned
     * that weekday" — the Capacity Board needs to tell the two apart.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> findNonWorkingExceptionDates(UUID workCalendarId, LocalDate from, LocalDate to) {
        WorkCalendar calendar = loadWithExceptions(workCalendarId);
        return calendar.getExceptions().stream()
                .map(WorkCalendarException::getExceptionDate)
                .filter(date -> !date.isBefore(from) && !date.isAfter(to))
                .collect(Collectors.toSet());
    }

    /**
     * Loads the calendar for {@link WorkingWindowCalculator}: weekly shift rows join-fetched, the
     * rest resolved lazily inside this read-only transaction.
     *
     * <p>Only ONE bag may be join-fetched per query, and this aggregate reaches three:
     * {@code weeklyShifts}, {@code exceptions}, and {@code Shift.breaks} one association further
     * out. The entity graph takes {@code weeklyShifts} (see
     * {@link WorkCalendarRepository#findWithWeeklyShiftsByWorkCalendarId}); the other two resolve
     * lazily while the session is still open.
     *
     * <p>{@code shift.breaks} deliberately has NO explicit touch here even though the calculator
     * subtracts breaks from every shift window. A draft of the 2026-08-14 fix added one and a
     * mutation run disproved it: removing the loop left all four cases of
     * {@code WorkCalendarLookupServiceIT} green, because the calculator runs inside this same
     * transaction and triggers the load itself, at identical query cost. It was noise that read
     * like a safeguard. {@code exceptions} keeps its pre-existing touch — that one predates the fix
     * and is out of its scope.
     */
    /**
     * Content fingerprint of the calendars a schedule was computed on (G8a, D9): the first 16 hex
     * characters of a SHA-256 over each calendar's status, effective range, weekly shifts (with their
     * times and breaks) and non-working dates, in a fixed order. Built from content rather than from
     * {@code version} columns because the weekly shifts, breaks and exceptions are child rows whose edits
     * do not necessarily move the parent's version. {@code null} when no calendar was consulted.
     */
    @Transactional(readOnly = true)
    public String calendarRevision(Collection<UUID> workCalendarIds) {
        if (workCalendarIds == null || workCalendarIds.isEmpty()) {
            return null;
        }
        StringBuilder canonical = new StringBuilder();
        for (UUID calendarId : new TreeSet<>(workCalendarIds)) {
            WorkCalendar calendar = loadWithExceptions(calendarId);
            canonical.append(calendarId).append('|').append(calendar.getStatus())
                    .append('|').append(calendar.getEffectiveFrom()).append('|').append(calendar.getEffectiveTo());
            calendar.getWeeklyShifts().stream()
                    .map(weekly -> weekly.getWeekday() + "@" + weekly.getShift().getShiftId()
                            + "/" + weekly.getShift().getStartTime() + "-" + weekly.getShift().getEndTime()
                            + weekly.getShift().getBreaks().stream()
                            .map(shiftBreak -> shiftBreak.getStartTime() + "~" + shiftBreak.getEndTime())
                            .sorted()
                            .collect(Collectors.joining(",", "[", "]")))
                    .sorted()
                    .forEach(entry -> canonical.append('|').append(entry));
            calendar.getExceptions().stream()
                    .map(exception -> exception.getExceptionDate().toString())
                    .sorted()
                    .forEach(date -> canonical.append("|x").append(date));
            canonical.append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    private WorkCalendar loadWithExceptions(UUID workCalendarId) {
        WorkCalendar calendar = workCalendarRepository.findWithWeeklyShiftsByWorkCalendarId(workCalendarId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Work calendar", workCalendarId));
        calendar.getExceptions().size();
        return calendar;
    }
}
