package com.erp.manufacturing.module.uom.mapper;

import com.erp.manufacturing.module.uom.domain.Uom;
import com.erp.manufacturing.module.uom.dto.UomResponse;
import org.springframework.stereotype.Component;

@Component
public class UomMapper {

    public UomResponse toResponse(Uom uom) {
        return new UomResponse(
                uom.getUomId(),
                uom.getCode(),
                uom.getName(),
                uom.getDescription(),
                uom.getStatus().name(),
                uom.getCreatedAt(),
                uom.getUpdatedAt());
    }
}
