package com.erp.manufacturing.module.bom.mapper;

import com.erp.manufacturing.module.bom.domain.BomHeader;
import com.erp.manufacturing.module.bom.domain.BomLine;
import com.erp.manufacturing.module.bom.dto.BomLineResponse;
import com.erp.manufacturing.module.bom.dto.BomResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                lines,
                bom.getBomCode(),
                bom.getVersionNumber(),
                bom.getOutputQuantity(),
                bom.getParentItem().getUnit(),
                bom.getEffectiveFrom(),
                bom.getVersion());
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
                line.getScrapRate(),
                componentQuantity(line.getQuantityPer(), line.getBom().getOutputQuantity()),
                line.getComponentItem().getUnit());
    }

    /**
     * {@code quantityPer × outputQuantity} at the column scale. Exact for every line the atomic save
     * wrote (it refuses a quotient that needs more than 6 decimals), so the figure a user typed comes
     * back unchanged; HALF_UP only ever matters for a line written per unit through the old endpoints.
     */
    public static BigDecimal componentQuantity(BigDecimal quantityPer, BigDecimal outputQuantity) {
        BigDecimal output = outputQuantity == null ? BigDecimal.ONE : outputQuantity;
        return quantityPer.multiply(output).setScale(6, RoundingMode.HALF_UP);
    }
}
