package com.erp.manufacturing.module.dataimport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of {@code POST /v1/data-import/batches/{id}/commit}. Deliberately nothing else: rows, file and
 * owner all come from the batch the server stored (FE R03).
 */
public record DataImportBatchCommitRequest(@NotNull @PositiveOrZero Long expectedVersion) {
}
