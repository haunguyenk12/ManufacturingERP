package com.erp.manufacturing.module.workcenter.mapper;

import com.erp.manufacturing.module.workcenter.domain.WorkCenter;
import com.erp.manufacturing.module.workcenter.dto.WorkCenterResponse;
import org.springframework.stereotype.Component;

@Component
public class WorkCenterMapper {

    public WorkCenterResponse toResponse(WorkCenter workCenter) {
        return new WorkCenterResponse(
                workCenter.getWorkCenterId(),
                workCenter.getPlant().getPlantId(),
                workCenter.getCode(),
                workCenter.getName(),
                workCenter.getDescription(),
                workCenter.getCapacityUnitType().name(),
                workCenter.getCapacityUnits(),
                workCenter.getStatus().name(),
                workCenter.getVersion(),
                workCenter.getCreatedAt(),
                workCenter.getUpdatedAt(),
                workCenter.getWorkCalendar() == null ? null : workCenter.getWorkCalendar().getWorkCalendarId());
    }
}
