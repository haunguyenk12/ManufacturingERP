package com.erp.manufacturing.module.shift.mapper;

import com.erp.manufacturing.module.shift.domain.WorkCalendar;
import com.erp.manufacturing.module.shift.domain.WorkCalendarException;
import com.erp.manufacturing.module.shift.domain.WorkCalendarWeeklyShift;
import com.erp.manufacturing.module.shift.dto.WorkCalendarExceptionResponse;
import com.erp.manufacturing.module.shift.dto.WorkCalendarResponse;
import com.erp.manufacturing.module.shift.dto.WorkCalendarWeeklyShiftResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class WorkCalendarMapper {

    public WorkCalendarResponse toResponse(WorkCalendar calendar) {
        List<WorkCalendarWeeklyShiftResponse> weeklyShifts = calendar.getWeeklyShifts() == null
                ? List.of()
                : calendar.getWeeklyShifts().stream()
                .sorted(Comparator.comparing(WorkCalendarWeeklyShift::getWeekday))
                .map(this::toResponse)
                .toList();

        List<WorkCalendarExceptionResponse> exceptions = calendar.getExceptions() == null
                ? List.of()
                : calendar.getExceptions().stream()
                .sorted(Comparator.comparing(WorkCalendarException::getExceptionDate))
                .map(this::toResponse)
                .toList();

        return new WorkCalendarResponse(
                calendar.getWorkCalendarId(),
                calendar.getPlant().getPlantId(),
                calendar.getCode(),
                calendar.getName(),
                calendar.getEffectiveFrom(),
                calendar.getEffectiveTo(),
                calendar.getStatus().name(),
                weeklyShifts,
                exceptions,
                calendar.getVersion(),
                calendar.getCreatedAt(),
                calendar.getUpdatedAt());
    }

    private WorkCalendarWeeklyShiftResponse toResponse(WorkCalendarWeeklyShift weeklyShift) {
        return new WorkCalendarWeeklyShiftResponse(
                weeklyShift.getWorkCalendarWeeklyShiftId(),
                weeklyShift.getWeekday(),
                weeklyShift.getShift().getShiftId(),
                weeklyShift.getShift().getCode());
    }

    private WorkCalendarExceptionResponse toResponse(WorkCalendarException exception) {
        return new WorkCalendarExceptionResponse(
                exception.getWorkCalendarExceptionId(),
                exception.getExceptionDate(),
                exception.getReason());
    }
}
