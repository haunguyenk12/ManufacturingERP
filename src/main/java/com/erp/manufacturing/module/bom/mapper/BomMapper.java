package com.erp.manufacturing.module.bom.mapper;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.dto.BomLineResponse;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class BomMapper {

    public BomResponse toResponse(BomHeader bom) {
        List<BomLineResponse> lines = bom.getLines() == null
                ? List.of()
                : bom.getLines().stream()
                .sorted(Comparator.comparing(BomLine::getLineNo))
                .map(this::toResponse)
                .toList();

        return new BomResponse(
                bom.getBomId(),
                bom.getCompany().getCompanyId(),
                bom.getParentItem().getItemId(),
                bom.getParentItem().getCode(),
                bom.getParentItem().getName(),
                bom.getRevision(),
                bom.getStatus().name(),
                bom.getDescription(),
                bom.getCreatedAt(),
                bom.getUpdatedAt(),
                lines);
    }

    public BomLineResponse toResponse(BomLine line) {
        return new BomLineResponse(
                line.getLineId(),
                line.getBom().getBomId(),
                line.getLineNo(),
                line.getComponentItem().getItemId(),
                line.getComponentItem().getCode(),
                line.getComponentItem().getName(),
                line.getQuantityPer(),
                line.getScrapRate());
    }
}
