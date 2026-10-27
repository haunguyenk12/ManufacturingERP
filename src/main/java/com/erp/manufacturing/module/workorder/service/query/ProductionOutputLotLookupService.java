package com.erp.manufacturing.module.workorder.service.query;

import com.erp.manufacturing.module.workorder.repository.ProductionQualityDecisionRepository;
import com.erp.manufacturing.module.workorder.repository.ProductionReceiptLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * G10d: entry point for {@code module/delivery} into production provenance (rule C7) — the lots a production receipt
 * (or every receipt of a work order) put into stock: the APPROVED output lot and the child lots its QC accepted. No
 * {@code @PreAuthorize}: the caller lists only its own plant's deliveries.
 */
@Service
@RequiredArgsConstructor
public class ProductionOutputLotLookupService {

    private final ProductionReceiptLineRepository receiptLineRepository;
    private final ProductionQualityDecisionRepository decisionRepository;

    @Transactional(readOnly = true)
    public Set<UUID> findOutputLotIds(UUID receiptId, UUID workOrderId) {
        Set<UUID> lots = new LinkedHashSet<>(receiptLineRepository.findApprovedOutputLotIds(receiptId, workOrderId));
        lots.addAll(decisionRepository.findAcceptedLotIds(receiptId, workOrderId));
        return lots;
    }
}
