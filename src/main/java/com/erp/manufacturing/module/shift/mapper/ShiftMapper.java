package com.erp.manufacturing.module.shift.mapper;

import com.erp.manufacturing.module.shift.domain.Shift;
import com.erp.manufacturing.module.shift.domain.ShiftBreak;
import com.erp.manufacturing.module.shift.dto.ShiftBreakResponse;
import com.erp.manufacturing.module.shift.dto.ShiftResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ShiftMapper {

    public ShiftResponse toResponse(Shift shift) {
        List<ShiftBreakResponse> breaks = shift.getBreaks() == null
                ? List.of()
                : shift.getBreaks().stream()
                .sorted(Comparator.comparing(ShiftBreak::getStartTime))
                .map(this::toResponse)
                .toList();

        return new ShiftResponse(
                shift.getShiftId(),
                shift.getPlant().getPlantId(),
                shift.getCode(),
                shift.getName(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getStatus().name(),
                breaks,
                shift.getVersion(),
                shift.getCreatedAt(),
                shift.getUpdatedAt());
    }

    private ShiftBreakResponse toResponse(ShiftBreak shiftBreak) {
        return new ShiftBreakResponse(
                shiftBreak.getShiftBreakId(),
                shiftBreak.getStartTime(),
                shiftBreak.getEndTime());
    }
}
