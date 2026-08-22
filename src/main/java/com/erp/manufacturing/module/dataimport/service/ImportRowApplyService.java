package com.erp.manufacturing.module.dataimport.service;

import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportCellError;
import com.erp.manufacturing.module.dataimport.domain.ImportErrorCode;
import com.erp.manufacturing.module.dataimport.domain.ImportRow;
import com.erp.manufacturing.module.dataimport.domain.ImportRowStatus;
import com.erp.manufacturing.module.dataimport.repository.ImportRowRepository;
import com.erp.manufacturing.module.dataimport.target.ImportTargetRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Keeps each applied row in its own transaction, as required by the import contract. */
@Service
@RequiredArgsConstructor
public class ImportRowApplyService {

    private final ImportRowRepository rowRepository;
    private final ImportTargetRegistry registry;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID apply(UUID rowId) {
        ImportRow row = findRow(rowId);
        if (row.getStatus() != ImportRowStatus.VALID) {
            throw ExceptionFactory.businessRule(ValidationErrorCode.INVALID_INPUT,
                    "Only a VALID import row can be applied: " + rowId);
        }
        UUID createdId = registry.handlerFor(row.getImportRun().getTargetType())
                .apply(row.getImportRun().getCompany().getCompanyId(), row.getMappedValues());
        row.markApplied(createdId);
        rowRepository.save(row);
        return createdId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID rowId, String message) {
        ImportRow row = findRow(rowId);
        row.markFailed(ImportCellError.row(ImportErrorCode.APPLY_FAILED,
                message == null || message.isBlank() ? "Row could not be applied" : message));
        rowRepository.save(row);
    }

    private ImportRow findRow(UUID rowId) {
        return rowRepository.findById(rowId)
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "Import row", rowId));
    }
}
